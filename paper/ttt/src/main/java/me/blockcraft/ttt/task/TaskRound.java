package me.blockcraft.ttt.task;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.BlockChanges;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.arena.Chore;
import me.blockcraft.ttt.arena.TaskLayout;
import me.blockcraft.ttt.config.TttConfig;
import me.blockcraft.ttt.game.Channels;
import me.blockcraft.ttt.game.ChestLoot;
import me.blockcraft.ttt.game.Role;
import me.blockcraft.ttt.task.game.ChoreGames;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Bell;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * TTT 2's part of a round: the chores everyone gets with their role, marked in the world for each player alone, the
 * bar of how far the innocents are, and the traitors' sabotage. Every real chore done pays one pick of loot, a visible
 * one shows itself at its station, and when the living innocents have done all of theirs, {@code changed} finds them
 * the winners. A click on a chore answers on the action bar; the chat stays for what concerns everyone.
 */
public final class TaskRound {

    private static final long SECOND = 20L;

    private final Plugin plugin;
    private final TttConfig.Tasks config;
    private final Messages messages;
    private final TaskLayout layout;
    private final Channels channels;
    private final Supplier<List<Player>> alive;
    private final Predicate<Player> traitor;
    private final Runnable changed;
    private final TaskBoard board = new TaskBoard();
    private final Sabotage sabotage;
    private final BossBar bar;
    private final ChoreMarkers markers;
    private final Component choreMark;
    private final Component deliverMark;
    private final Component repairMark;
    private final Component traitorMark;
    private final List<Block> traitorSpots;
    private BukkitTask ticker;

    public TaskRound(Plugin plugin, TttConfig.Tasks config, Messages messages, TaskLayout layout, Block testerButton,
                     List<Block> traitorSpots, Channels channels, Function<Player, Optional<Role>> roles,
                     Supplier<List<Player>> alive, Runnable changed) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.layout = layout;
        this.channels = channels;
        this.alive = alive;
        this.changed = changed;
        this.traitor = player -> roles.apply(player).filter(role -> role == Role.TRAITOR).isPresent();
        this.sabotage = new Sabotage(config.sabotage(), messages, layout, testerButton, channels, traitor, alive);
        this.bar = BossBar.bossBar(messages.get("tasks.bar", Formatter.number("percent", 0)), 0,
                BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
        this.markers = new ChoreMarkers(plugin);
        this.choreMark = messages.get("tasks.marker.chore");
        this.deliverMark = messages.get("tasks.marker.deliver");
        this.repairMark = messages.get("tasks.marker.repair");
        this.traitorMark = messages.get("tasks.marker.traitor");
        this.traitorSpots = traitorSpots;
    }

    public void assign(Map<UUID, Role> roles) {
        var workers = roles.entrySet().stream().filter(entry -> entry.getValue() != Role.TRAITOR)
                .map(Map.Entry::getKey).toList();
        board.assign(workers, layout.chores(), config.perPlayer(), this::close, ThreadLocalRandom.current());
        Bukkit.getOnlinePlayers().forEach(player -> player.showBossBar(bar));
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            sabotage.tick();
            Bukkit.getOnlinePlayers().forEach(this::mark);
            markers.sparkle();
        }, SECOND, SECOND);
        update();
    }

    public Sabotage sabotage() {
        return sabotage;
    }

    /**
     * A click on part of the chores or the sabotage: a station, a destination, the generator, a broken tester or a
     * locked door. True if the click was theirs, so the world must not answer it.
     */
    public boolean click(Player player, Block block) {
        if (sabotage.locked(block)) {
            tell(player, messages.get("tasks.sabotage.locked"));
            Sounds.MENU_DENY.play(player);
            return true;
        }
        if (sabotage.mend(player, block)) {
            return true;
        }
        if (block.equals(layout.generator())) {
            tell(player, messages.get("tasks.generator"));
            return true;
        }
        var station = layout.chores().stream().filter(chore -> chore.station().equals(block)).findFirst();
        var destination = layout.chores().stream()
                .filter(chore -> chore instanceof Chore.Fetch fetch && fetch.destination().equals(block)).findFirst();
        if (station.isEmpty() && destination.isEmpty()) {
            return false;
        }
        if (!board.assigned()) {
            tell(player, messages.get("tasks.not-yet"));
            return true;
        }
        var id = player.getUniqueId();
        var duty = board.open(id, chore -> chore.station().equals(block)
                || chore instanceof Chore.Fetch fetch && fetch.destination().equals(block));
        if (duty.isEmpty()) {
            var chore = station.or(() -> destination).orElseThrow();
            boolean done = board.of(id).stream().anyMatch(each -> each.done() && each.chore().equals(chore));
            tell(player, messages.get(done ? "tasks.already-done" : "tasks.not-yours", names(chore)));
            return true;
        }
        var chore = duty.get().chore();
        switch (chore) {
            case Chore.Hold hold -> {
                if (!channels.busy(player)) {
                    channels.start(player, center(hold.station()), config.holdSeconds(), messages.get("tasks.chores." + hold.key() + ".name"),
                            () -> finish(player, hold));
                }
            }
            case Chore.Fetch fetch -> carry(player, fetch, block);
            case Chore.Puzzle puzzle -> ChoreGames.of(puzzle.key(), plugin, messages, messages.get("tasks.game-title", names(puzzle)),
                    () -> finish(player, puzzle)).play(player);
        }
        return true;
    }

    public boolean testerDown() {
        return sabotage.testerDown();
    }

    public boolean complete() {
        return board.complete(living());
    }

    /** The player's chores for their sidebar, done ones ticked. */
    public List<Component> duties(Player player) {
        return board.of(player.getUniqueId()).stream()
                .map(duty -> messages.get(duty.done() ? "tasks.sidebar.done" : "tasks.sidebar.open", names(duty.chore())))
                .toList();
    }

    /** Shows the bar to someone who came to watch after the chores were handed out. */
    public void show(Player player) {
        if (board.assigned()) {
            player.showBossBar(bar);
            mark(player);
        }
    }

    /** The bar and the marks follow a death too, since a dead innocent's open chores drop out. */
    public void update() {
        float progress = board.progress(living());
        bar.progress(progress);
        bar.name(messages.get("tasks.bar", Formatter.number("percent", Math.round(progress * 100))));
        markers.keepOnly(Set.copyOf(living()));
        Bukkit.getOnlinePlayers().forEach(this::mark);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
        }
        sabotage.stop();
        markers.clear();
        Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(bar));
        board.clear();
    }

    private void carry(Player player, Chore.Fetch fetch, Block clicked) {
        boolean carrying = carrying(player, fetch);
        if (clicked.equals(fetch.destination())) {
            if (!carrying) {
                tell(player, messages.get("tasks.cargo-missing", names(fetch)));
                Sounds.MENU_DENY.play(player);
                return;
            }
            player.getInventory().all(fetch.cargo()).forEach((slot, stack) -> {
                if (TaskItem.chore(stack).filter(fetch.key()::equals).isPresent()) {
                    player.getInventory().clear(slot);
                }
            });
            finish(player, fetch);
            return;
        }
        if (carrying) {
            tell(player, messages.get("tasks.cargo-carrying", names(fetch)));
            return;
        }
        var cargo = TaskItem.CARGO.forChore(Items.of(fetch.cargo())
                .name(messages.get("tasks.chores." + fetch.key() + ".cargo")).lore(messages.get("tasks.chores." + fetch.key() + ".name"))
                .build(), fetch.key());
        player.getInventory().addItem(cargo).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        tell(player, messages.get("tasks.cargo-taken", names(fetch)));
        Sounds.EQUIP.play(player);
        mark(player);
    }

    private void finish(Player player, Chore chore) {
        var duty = board.open(player.getUniqueId(), chore::equals);
        if (duty.isEmpty() || !alive.get().contains(player)) {
            return;
        }
        board.finish(duty.get());
        Sounds.ACHIEVEMENT.play(player);
        var loot = ChestLoot.pick(config.loot(), ThreadLocalRandom.current()).map(ChestLoot::item);
        loot.ifPresent(item -> player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left)));
        BlockcraftApi.get().notifications().reward(player, messages.get("tasks.done", names(chore)));
        if (chore.visible()) {
            show(chore.station());
        }
        update();
        changed.run();
    }

    private void show(Block station) {
        if (station.getState() instanceof Bell bell) {
            bell.ring();
        } else if (station.getBlockData() instanceof Lightable light && !light.isLit()) {
            light.setLit(true);
            BlockChanges.set(station, light);
        }
        var center = center(station);
        station.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 24, 0.5, 0.6, 0.5);
        station.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.2f, 1.4f);
    }

    /**
     * Marks where {@code player} goes next: each open chore's station, or for cargo they carry, where it goes, and
     * where the running sabotage is mended. A traitor has no chores and knows their own sabotage; they see their
     * passages' doors and the tester's trap button instead.
     */
    private void mark(Player player) {
        if (!board.assigned() || !alive.get().contains(player)) {
            markers.hide(player.getUniqueId());
            return;
        }
        var spots = new HashMap<Block, Component>();
        if (traitor.test(player)) {
            traitorSpots.forEach(spot -> spots.put(spot, traitorMark));
            markers.show(player, spots);
            return;
        }
        board.of(player.getUniqueId()).stream().filter(duty -> !duty.done()).map(TaskBoard.Duty::chore)
                .forEach(chore -> {
                    if (chore instanceof Chore.Fetch fetch && carrying(player, fetch)) {
                        spots.put(fetch.destination(), deliverMark);
                    } else {
                        spots.put(chore.station(), choreMark);
                    }
                });
        sabotage.repairSpot().ifPresent(spot -> spots.put(spot, repairMark));
        markers.show(player, spots);
    }

    private boolean close(Chore one, Chore other) {
        var at = one.station().getLocation();
        var near = other.station().getLocation();
        return at.getWorld().equals(near.getWorld()) && at.distanceSquared(near) < (double) config.spread() * config.spread();
    }

    private static boolean carrying(Player player, Chore.Fetch fetch) {
        return player.getInventory().all(fetch.cargo()).values().stream()
                .anyMatch(stack -> TaskItem.chore(stack).filter(fetch.key()::equals).isPresent());
    }

    private static void tell(Player player, Component message) {
        BlockcraftApi.get().notifications().feedback(player, message);
    }

    private TagResolver names(Chore chore) {
        return TagResolver.resolver(Placeholder.component("chore", messages.get("tasks.chores." + chore.key() + ".name")),
                Placeholder.component("where", messages.get("tasks.chores." + chore.key() + ".where")));
    }

    private List<UUID> living() {
        return alive.get().stream().map(Player::getUniqueId).toList();
    }

    private static Location center(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }
}
