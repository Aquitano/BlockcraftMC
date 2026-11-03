package me.blockcraft.ttt.task;

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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * TTT 2's part of a round: the chores everyone gets with their role, the bar of how far the innocents are, and the
 * traitors' sabotage. Every real chore done pays one pick of loot, a visible one shows itself at its station, and
 * when the living innocents have done all of theirs, {@code changed} finds them the winners.
 */
public final class TaskRound {

    private static final long SECOND = 20L;

    private final Plugin plugin;
    private final TttConfig.Tasks config;
    private final Messages messages;
    private final TaskLayout layout;
    private final Channels channels;
    private final Supplier<List<Player>> alive;
    private final Runnable changed;
    private final TaskBoard board = new TaskBoard();
    private final Sabotage sabotage;
    private final BossBar bar;
    private BukkitTask ticker;

    public TaskRound(Plugin plugin, TttConfig.Tasks config, Messages messages, TaskLayout layout, Block testerButton,
                     Channels channels, Function<Player, Optional<Role>> roles, Supplier<List<Player>> alive,
                     Runnable changed) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.layout = layout;
        this.channels = channels;
        this.alive = alive;
        this.changed = changed;
        this.sabotage = new Sabotage(config.sabotage(), messages, layout, testerButton, channels,
                player -> roles.apply(player).filter(role -> role == Role.TRAITOR).isPresent(), alive);
        this.bar = BossBar.bossBar(messages.get("tasks.bar", Formatter.number("percent", 0)), 0,
                BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
    }

    /** What everyone carries from the start, since the chests stay shut. */
    public void equip(Player player) {
        config.startItems().forEach(material -> player.getInventory().addItem(ItemStack.of(material)));
    }

    public void assign(Map<UUID, Role> roles) {
        var traitors = roles.entrySet().stream().filter(entry -> entry.getValue() == Role.TRAITOR)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        board.assign(roles.keySet(), traitors, layout.chores(), config.perPlayer(), ThreadLocalRandom.current());
        Bukkit.getOnlinePlayers().forEach(player -> player.showBossBar(bar));
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, sabotage::tick, SECOND, SECOND);
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
            player.sendMessage(messages.get("tasks.sabotage.locked"));
            Sounds.MENU_DENY.play(player);
            return true;
        }
        if (sabotage.mend(player, block)) {
            return true;
        }
        if (block.equals(layout.generator())) {
            player.sendMessage(messages.get("tasks.generator"));
            return true;
        }
        var station = layout.chores().stream().filter(chore -> chore.station().equals(block)).findFirst();
        var destination = layout.chores().stream()
                .filter(chore -> chore instanceof Chore.Fetch fetch && fetch.destination().equals(block)).findFirst();
        if (station.isEmpty() && destination.isEmpty()) {
            return false;
        }
        if (!board.assigned()) {
            player.sendMessage(messages.get("tasks.not-yet"));
            return true;
        }
        var id = player.getUniqueId();
        var duty = board.open(id, chore -> chore.station().equals(block)
                || chore instanceof Chore.Fetch fetch && fetch.destination().equals(block));
        if (duty.isEmpty()) {
            var chore = station.or(() -> destination).orElseThrow();
            player.sendMessage(messages.get("tasks.not-yours", names(chore)));
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
            case Chore.Puzzle puzzle -> new PuzzleMenu(messages, messages.get("tasks.puzzle.title", names(puzzle)), () -> finish(player, puzzle))
                    .open(player, () -> alive.get().contains(player) && board.open(id, puzzle::equals).isPresent());
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
        }
    }

    /** The bar follows a death too, since a dead innocent's open chores drop out. */
    public void update() {
        float progress = board.progress(living());
        bar.progress(progress);
        bar.name(messages.get("tasks.bar", Formatter.number("percent", Math.round(progress * 100))));
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
        }
        sabotage.stop();
        Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(bar));
        board.clear();
    }

    private void carry(Player player, Chore.Fetch fetch, Block clicked) {
        boolean carrying = player.getInventory().all(fetch.cargo()).values().stream()
                .anyMatch(stack -> TaskItem.chore(stack).filter(fetch.key()::equals).isPresent());
        if (clicked.equals(fetch.destination())) {
            if (!carrying) {
                player.sendMessage(messages.get("tasks.cargo-missing", names(fetch)));
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
            player.sendMessage(messages.get("tasks.cargo-carrying", names(fetch)));
            return;
        }
        var cargo = TaskItem.CARGO.forChore(Items.of(fetch.cargo())
                .name(messages.get("tasks.chores." + fetch.key() + ".cargo")).lore(messages.get("tasks.chores." + fetch.key() + ".name"))
                .build(), fetch.key());
        player.getInventory().addItem(cargo).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        player.sendMessage(messages.get("tasks.cargo-taken", names(fetch)));
        Sounds.EQUIP.play(player);
    }

    private void finish(Player player, Chore chore) {
        var duty = board.open(player.getUniqueId(), chore::equals);
        if (duty.isEmpty() || !alive.get().contains(player)) {
            return;
        }
        boolean real = board.finish(player.getUniqueId(), duty.get());
        Sounds.ACHIEVEMENT.play(player);
        if (!real) {
            player.sendMessage(messages.get("tasks.done-pretend", names(chore)));
            changed.run();
            return;
        }
        var loot = ChestLoot.pick(config.loot(), ThreadLocalRandom.current()).map(ChestLoot::item);
        loot.ifPresent(item -> player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left)));
        player.sendMessage(messages.get("tasks.done", names(chore)));
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
