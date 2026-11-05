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
import me.blockcraft.ttt.game.RoundItem;
import me.blockcraft.ttt.task.game.ChoreGame;
import me.blockcraft.ttt.task.game.ChoreGames;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Bell;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The team's fixed task pool, personal routes and selected objectives. Every completion pays loot and advances the
 * team target. Sabotage temporarily takes over the task bar and navigation.
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
    private final Map<UUID, ChoreGame> games = new HashMap<>();
    private final Map<UUID, Chore> selected = new HashMap<>();
    private final Sabotage sabotage;
    private final BossBar bar;
    private enum Mark {
        CHORE, DELIVER, REPAIR, DOOR, TRAP;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final double HINT_REACH = 5.0;

    private record Objective(Block block, Mark mark, Component name, Component place) {
    }

    private final ChoreMarkers markers;
    private final Map<Mark, Component> marks = new EnumMap<>(Mark.class);
    private final Map<Mark, Component> hints = new EnumMap<>(Mark.class);
    private final List<Block> traitorDoors;
    private final Optional<Block> trapButton;
    private final BooleanSupplier trapUsed;
    private BukkitTask ticker;

    public TaskRound(Plugin plugin, TttConfig.Tasks config, Messages messages, TaskLayout layout, Block testerButton,
                     List<Block> traitorDoors, Optional<Block> trapButton, BooleanSupplier trapUsed, Channels channels,
                     Function<Player, Optional<Role>> roles, Supplier<List<Player>> alive, Runnable changed) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.layout = layout;
        this.channels = channels;
        this.alive = alive;
        this.changed = changed;
        this.traitor = player -> roles.apply(player).filter(role -> role == Role.TRAITOR).isPresent();
        this.bar = BossBar.bossBar(messages.get("tasks.bar", Formatter.number("percent", 0)), 0,
                BossBar.Color.GREEN, BossBar.Overlay.NOTCHED_10);
        this.sabotage = new Sabotage(config.sabotage(), messages, layout, testerButton, channels, traitor, alive, bar, this::update);
        this.markers = new ChoreMarkers(plugin);
        for (var mark : Mark.values()) {
            marks.put(mark, messages.get("tasks.marker." + mark.key()));
            hints.put(mark, messages.get("tasks.hint." + mark.key()));
        }
        this.traitorDoors = traitorDoors;
        this.trapButton = trapButton;
        this.trapUsed = trapUsed;
    }

    public void assign(Map<UUID, Role> roles) {
        var workers = roles.entrySet().stream().filter(entry -> entry.getValue() != Role.TRAITOR)
                .map(Map.Entry::getKey).toList();
        board.assign(workers, layout.chores(), config.perPlayer(), this::close, ThreadLocalRandom.current());
        alive.get().forEach(this::giveTasks);
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
        selected.put(id, chore);
        switch (chore) {
            case Chore.Hold hold -> {
                if (!channels.busy(player)) {
                    channels.start(player, center(hold.station()), config.holdSeconds(), messages.get("tasks.chores." + hold.key() + ".name"),
                            () -> finish(player, hold));
                }
            }
            case Chore.Fetch fetch -> carry(player, fetch, block);
            case Chore.Puzzle puzzle -> {
                var playing = games.get(id);
                if (playing == null || !playing.busy(player)) {
                    var game = ChoreGames.of(puzzle.key(), plugin, messages, messages.get("tasks.game-title",
                            Placeholder.component("how", messages.get("tasks.games." + puzzle.key() + ".how"))),
                            () -> finish(player, puzzle));
                    games.put(id, game);
                    game.play(player);
                }
            }
        }
        return true;
    }

    public boolean testerDown() {
        return sabotage.testerDown();
    }

    public boolean complete() {
        return board.complete();
    }

    public List<Component> duties(Player player) {
        if (!board.assigned()) {
            return List.of();
        }
        if (traitor.test(player)) {
            int seconds = sabotage.secondsUntilReady();
            var lines = new ArrayList<>(List.of(messages.get("tasks.sidebar.traitor"), messages.get(seconds == 0
                    ? "tasks.sidebar.sabotage-ready" : "tasks.sidebar.sabotage-wait", Formatter.number("seconds", seconds))));
            focus(player).ifPresent(objective -> {
                lines.add(objective.name());
                lines.add(navigation(player, objective));
            });
            return lines;
        }
        var lines = new ArrayList<Component>();
        lines.add(messages.get("tasks.sidebar.progress", Formatter.number("done", board.contribution(player.getUniqueId()))));
        focus(player).ifPresent(objective -> {
            lines.add(objective.name());
            lines.add(navigation(player, objective));
        });
        return lines;
    }

    public void giveTasks(Player player) {
        if (!board.assigned()) {
            return;
        }
        var looted = player.getInventory().getItem(RoundItem.TASKS_SLOT);
        player.getInventory().setItem(RoundItem.TASKS_SLOT, RoundItem.TASKS.build(Items.of(Material.WRITABLE_BOOK)
                .name(messages.get("item.tasks")).lore(messages.get("tasks.menu.hint"))));
        if (looted != null && !looted.isEmpty()) {
            player.getInventory().addItem(looted).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
    }

    public void openTasks(Player player) {
        if (!board.assigned() || !alive.get().contains(player)) {
            return;
        }
        var mine = board.of(player.getUniqueId()).stream().map(TaskBoard.Duty::chore).toList();
        var choices = layout.chores().stream().map(chore -> {
            var objective = objective(player, chore);
            int remaining = board.remaining(chore);
            var lore = new ArrayList<Component>();
            lore.add(navigation(player, objective));
            if (mine.contains(chore)) {
                lore.add(messages.get("tasks.menu.recommended"));
            }
            lore.add(messages.get(remaining == 0 ? "tasks.already-done" : "tasks.menu.select"));
            var material = chore instanceof Chore.Fetch fetch ? fetch.cargo() : chore.station().getType();
            var icon = Items.of(material.isItem() ? material : Material.PAPER)
                    .name(messages.get("tasks.chores." + chore.key() + ".name")).lore(lore);
            if (chore.equals(selected.get(player.getUniqueId()))) {
                icon.glow();
            }
            return new TaskMenu.Choice(chore, icon.build());
        }).toList();
        new TaskMenu(messages.get("tasks.menu.title"), choices, chore -> {
            if (!alive.get().contains(player) || board.remaining(chore) == 0) {
                tell(player, messages.get("tasks.already-done"));
                return;
            }
            selected.put(player.getUniqueId(), chore);
            mark(player);
        }).open(player, () -> board.assigned() && alive.get().contains(player));
    }

    /** Shows the bar to someone who came to watch after the chores were handed out. */
    public void show(Player player) {
        if (board.assigned()) {
            player.showBossBar(bar);
            mark(player);
        }
    }

    public void update() {
        if (sabotage.active().isEmpty()) {
            bar.color(BossBar.Color.GREEN).overlay(BossBar.Overlay.NOTCHED_10).progress(board.progress());
            bar.name(messages.get("tasks.bar", Formatter.number("percent", Math.round(board.progress() * 100))));
        }
        for (var chore : layout.chores()) {
            if (chore instanceof Chore.Fetch fetch && board.remaining(chore) == 0) {
                alive.get().forEach(player -> removeCargo(player, fetch));
            }
        }
        markers.keepOnly(Set.copyOf(living()));
        Bukkit.getOnlinePlayers().forEach(this::mark);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
        }
        sabotage.stop();
        markers.clear();
        games.clear();
        selected.clear();
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
            removeCargo(player, fetch);
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
        selected.put(player.getUniqueId(), fetch);
        mark(player);
    }

    private void finish(Player player, Chore chore) {
        if (!alive.get().contains(player)) {
            return;
        }
        if (!board.finish(player.getUniqueId(), chore)) {
            tell(player, messages.get("tasks.already-done"));
            return;
        }
        selected.remove(player.getUniqueId(), chore);
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

    private void mark(Player player) {
        if (!board.assigned() || !alive.get().contains(player)) {
            markers.hide(player.getUniqueId());
            return;
        }
        var spots = new HashMap<Block, Mark>();
        if (traitor.test(player)) {
            traitorDoors.forEach(door -> spots.put(door, Mark.DOOR));
            trapButton.filter(button -> !trapUsed.getAsBoolean()).ifPresent(button -> spots.put(button, Mark.TRAP));
        } else {
            board.of(player.getUniqueId()).stream().filter(duty -> !duty.done()).map(TaskBoard.Duty::chore)
                    .map(chore -> objective(player, chore)).forEach(objective -> spots.put(objective.block(), objective.mark()));
        }
        var focus = focus(player);
        focus.ifPresent(objective -> spots.put(objective.block(), objective.mark()));
        var shown = new HashMap<Block, ChoreMarkers.Marker>();
        spots.forEach((spot, mark) -> shown.put(spot, new ChoreMarkers.Marker(marks.get(mark), false)));
        focus.ifPresent(objective -> shown.put(objective.block(), new ChoreMarkers.Marker(messages.get("tasks.focus",
                Placeholder.component("name", objective.name()), Placeholder.component("navigation", navigation(player, objective))), true)));
        markers.show(player, shown);
        if (!channels.busy(player)) {
            var hit = player.rayTraceBlocks(HINT_REACH, FluidCollisionMode.NEVER);
            if (hit == null || hit.getHitBlock() == null) {
                return;
            }
            var block = hit.getHitBlock();
            if (!spots.containsKey(block) && block.getBlockData() instanceof Door) {
                block = block.getRelative(BlockFace.DOWN);
            }
            var hint = spots.get(block);
            if (hint == null && !traitor.test(player)) {
                var target = block;
                hint = board.available(player.getUniqueId()).stream().map(chore -> objective(player, chore))
                        .filter(objective -> objective.block().equals(target)).map(Objective::mark).findFirst().orElse(null);
            }
            if (hint != null && !(traitor.test(player) && (hint == Mark.CHORE || hint == Mark.DELIVER))) {
                BlockcraftApi.get().notifications().status(player, hints.get(hint));
            }
        }
    }

    private Optional<Objective> focus(Player player) {
        var id = player.getUniqueId();
        if (!board.assigned() || !alive.get().contains(player)) {
            return Optional.empty();
        }
        if (traitor.test(player)) {
            return Optional.ofNullable(selected.get(id)).filter(chore -> board.remaining(chore) > 0)
                    .map(chore -> objective(player, chore));
        }
        var repair = sabotage.repairSpot();
        if (repair.isPresent()) {
            var kind = sabotage.active().orElseThrow();
            return Optional.of(new Objective(repair.get(), Mark.REPAIR, messages.get("tasks.sabotage.mending." + kind.key()),
                    messages.get("tasks.sabotage.where." + kind.key())));
        }
        var chore = selected.get(id);
        if (chore == null || board.remaining(chore) == 0) {
            var own = board.of(id).stream().filter(duty -> !duty.done()).map(TaskBoard.Duty::chore).toList();
            var next = board.available(id).stream().min(Comparator
                    .comparing((Chore each) -> !(each instanceof Chore.Fetch fetch && carrying(player, fetch)))
                    .thenComparing(each -> !own.contains(each))
                    .thenComparingDouble(each -> center(objective(player, each).block()).distanceSquared(player.getLocation())));
            if (next.isEmpty()) {
                selected.remove(id);
                return Optional.empty();
            }
            chore = next.get();
            selected.put(id, chore);
        }
        return Optional.of(objective(player, chore));
    }

    private Objective objective(Player player, Chore chore) {
        var key = "tasks.chores." + chore.key();
        if (chore instanceof Chore.Fetch fetch && carrying(player, fetch)) {
            return new Objective(fetch.destination(), Mark.DELIVER, messages.get(key + ".name"), messages.get(key + ".destination"));
        }
        return new Objective(chore.station(), Mark.CHORE, messages.get(key + ".name"), messages.get(key + ".where"));
    }

    private Component navigation(Player player, Objective objective) {
        var target = center(objective.block());
        double height = target.getY() - player.getLocation().getY();
        var vertical = messages.get("tasks.navigation." + (height > 3 ? "above" : height < -3 ? "below" : "level"));
        return messages.get("tasks.navigation.location", Placeholder.component("where", objective.place()),
                Formatter.number("distance", Math.round(target.distance(player.getLocation()))), Placeholder.component("vertical", vertical));
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

    private static void removeCargo(Player player, Chore.Fetch fetch) {
        player.getInventory().all(fetch.cargo()).forEach((slot, stack) -> {
            if (TaskItem.chore(stack).filter(fetch.key()::equals).isPresent()) {
                player.getInventory().clear(slot);
            }
        });
    }

    private static void tell(Player player, Component message) {
        BlockcraftApi.get().notifications().feedback(player, message);
    }

    private TagResolver names(Chore chore) {
        return TagResolver.resolver(Placeholder.component("chore", messages.get("tasks.chores." + chore.key() + ".name")),
                Placeholder.component("where", messages.get("tasks.chores." + chore.key() + ".where")),
                Placeholder.component("destination", chore instanceof Chore.Fetch
                        ? messages.get("tasks.chores." + chore.key() + ".destination") : Component.empty()));
    }

    private List<UUID> living() {
        return alive.get().stream().map(Player::getUniqueId).toList();
    }

    private static Location center(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }
}
