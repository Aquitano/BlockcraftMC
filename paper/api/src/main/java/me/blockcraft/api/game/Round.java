package me.blockcraft.api.game;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.vanish.VanishChangeEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.IntConsumer;

/**
 * The bookkeeping every game's round shares, held by the game next to its own rules. It seats every joiner: while
 * the lobby is open and has room, they play, unless they are vanished; everyone else watches, and a vanished
 * spectator is neither announced nor counted. A player who vanishes while the lobby waits moves to the spectators
 * the same way. It tells the proxy the phase, owns the countdowns and boss bars that
 * end with the round, counts a mid-round quit as a death and a loss, and ends the round once. While the round runs,
 * what a spectator writes reaches only the other spectators, so nobody can tell the players where their enemies are.
 * Spectators fly unseen in adventure mode with a hotbar to pick a player, step to the previous or next one and leave,
 * and {@link SpectatorGuard} keeps them off the round.
 */
public final class Round implements Listener {

    private static final Set<Integer> END_ANNOUNCEMENTS = Set.of(600, 300, 180, 60);
    private static final int SECONDS_PER_MINUTE = 60;
    private static final Set<UUID> WATCHING = ConcurrentHashMap.newKeySet();

    private final Plugin plugin;
    private final BlockcraftApi api;
    private final Arena arena;
    private final RoundSummary summary;
    private final int capacity;
    private final GameConfig config;
    // Chat reads these off the main thread; copy-on-write keeps the join order players() hands out.
    private final Set<UUID> players = new CopyOnWriteArraySet<>();
    private final Set<UUID> spectators = new CopyOnWriteArraySet<>();
    private final List<Countdown> timers = new ArrayList<>();
    private final List<TimerBar> bars = new ArrayList<>();
    private final Map<UUID, UUID> watched = new HashMap<>();
    private volatile GamePhase phase = GamePhase.LOBBY;

    public Round(Plugin plugin, Arena arena, RoundSummary summary, int capacity, GameConfig config) {
        this.plugin = plugin;
        this.api = BlockcraftApi.get();
        this.arena = arena;
        this.summary = summary;
        this.capacity = capacity;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(new SpectatorGuard(this::isSpectator), plugin);
    }

    /** Whether the player watches a round on this server, which their game mode no longer tells. */
    public static boolean isWatching(Player player) {
        return WATCHING.contains(player.getUniqueId());
    }

    public Arena arena() {
        return arena;
    }

    public RoundSummary summary() {
        return summary;
    }

    public GamePhase phase() {
        return phase;
    }

    /**
     * Open menus and dialogs close, because what they offered may be gone, and the proxy hears the new state. When
     * the lobby gives way to play, the summary learns who plays and everyone hears that the round started.
     */
    public void enter(GamePhase phase) {
        boolean starts = this.phase == GamePhase.LOBBY && phase == GamePhase.INGAME;
        this.phase = phase;
        Bukkit.getOnlinePlayers().forEach(player -> {
            Menu.close(player);
            player.closeDialog();
        });
        api.presence().state(phase.serverState());
        if (starts) {
            summary.begin(players());
            arena.announceStarted();
        }
    }

    /** Seats a joiner and returns whether they play; a spectator watches already. */
    public boolean join(Player player) {
        if (phase != GamePhase.LOBBY || players.size() >= capacity || api.isVanished(player)) {
            watch(player);
            arena.arrive(player);
            return false;
        }
        players.add(player.getUniqueId());
        WATCHING.remove(player.getUniqueId());
        // Whoever is online still hides this account if it watched here before and rejoined.
        for (var other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                other.showPlayer(plugin, player);
                if (isSpectator(other)) {
                    player.hidePlayer(plugin, other);
                }
            }
        }
        summary.join(player);
        arena.welcome(player, players.size(), capacity);
        return true;
    }

    /** Returns whether the player was playing; leaving a running round counts as a death and a loss. */
    public boolean leave(Player player) {
        var id = player.getUniqueId();
        spectators.remove(id);
        watched.remove(id);
        WATCHING.remove(id);
        bars.forEach(bar -> bar.hide(player));
        if (!players.remove(id)) {
            return false;
        }
        switch (phase) {
            case LOBBY -> summary.forget(player);
            case INGAME -> {
                summary.death(player);
                summary.eliminate(player);
            }
            case ENDING -> {
            }
        }
        arena.farewell(player, players.size(), capacity);
        return true;
    }

    @EventHandler
    public void onVanish(VanishChangeEvent event) {
        var player = event.getPlayer();
        if (!event.isVanished() || phase != GamePhase.LOBBY || !players.remove(player.getUniqueId())) {
            return;
        }
        summary.forget(player);
        watch(player);
        arena.farewell(player, players.size(), capacity);
    }

    /** Runs after core's chat format, whose line it marks rather than replaces. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (phase != GamePhase.INGAME || !isSpectator(event.getPlayer())) {
            return;
        }
        event.viewers().removeIf(viewer -> viewer instanceof Player reader && !isSpectator(reader));
        var tag = arena.spectatorChatTag().append(Component.space());
        var line = event.renderer();
        event.renderer((ChatRenderer) (source, displayName, message, viewer) ->
                tag.append(line.render(source, displayName, message, viewer)));
    }

    /** Out for good: the player watches the rest, once the game has respawned them and called {@link #watch}. */
    public void eliminate(Player player) {
        players.remove(player.getUniqueId());
        spectators.add(player.getUniqueId());
        summary.eliminate(player);
    }

    /**
     * Puts a spectator into the state they watch in: flying unseen by the players, with the spectator hotbar. Only
     * spectators see each other. A dead player is put into this state after respawning.
     */
    public void watch(Player player) {
        spectators.add(player.getUniqueId());
        WATCHING.add(player.getUniqueId());
        if (player.isDead()) {
            respawn(player, 1L);
            return;
        }
        PlayerReset.spectate(player);
        for (var other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) {
                continue;
            }
            if (isSpectator(other)) {
                player.showPlayer(plugin, other);
            } else {
                other.hidePlayer(plugin, player);
            }
        }
        SpectatorItem.give(player, api.messages());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        if (isSpectator(player) && isWatching(player)) {
            Tasks.sync(plugin, () -> {
                if (player.isOnline() && isSpectator(player)) {
                    watch(player);
                }
            });
        }
    }

    /** The spectator came to watch a friend, which the proxy names only after the join. */
    public void follow(Player spectator, UUID friend) {
        if (!isSpectator(spectator)) {
            return;
        }
        var target = Bukkit.getPlayer(friend);
        if (target != null && spectator.canSee(target)) {
            watched.put(spectator.getUniqueId(), friend);
            arena.follow(spectator, target);
        }
    }

    @EventHandler
    public void onSpectatorItem(PlayerInteractEvent event) {
        var spectator = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick() || !isSpectator(spectator)) {
            return;
        }
        HotbarItem.of(event.getItem(), SpectatorItem.class).ifPresent(item -> {
            event.setCancelled(true);
            switch (item) {
                case PICK_PLAYER -> pick(spectator);
                case PREVIOUS_PLAYER -> step(spectator, -1);
                case NEXT_PLAYER -> step(spectator, 1);
                case LEAVE_ROUND -> ProxyMessaging.hub(plugin, spectator);
            }
        });
    }

    private void pick(Player spectator) {
        var targets = watchable(spectator);
        if (targets.isEmpty()) {
            nobodyToWatch(spectator);
            return;
        }
        new SpectatorMenu(api.messages(), targets, this::jump).open(spectator);
    }

    /** Goes through the players in join order from the one last watched; without one, from the first or the last. */
    private void step(Player spectator, int step) {
        var targets = watchable(spectator);
        if (targets.isEmpty()) {
            nobodyToWatch(spectator);
            return;
        }
        int current = targets.stream().map(Player::getUniqueId).toList().indexOf(watched.get(spectator.getUniqueId()));
        int next = current < 0 ? (step > 0 ? 0 : targets.size() - 1) : Math.floorMod(current + step, targets.size());
        jump(spectator, targets.get(next));
    }

    private void jump(Player spectator, Player target) {
        if (!isSpectator(spectator) || !isPlaying(target) || !spectator.canSee(target)) {
            return;
        }
        watched.put(spectator.getUniqueId(), target.getUniqueId());
        arena.jump(spectator, target);
    }

    private List<Player> watchable(Player spectator) {
        return players().stream().filter(spectator::canSee).toList();
    }

    private void nobodyToWatch(Player spectator) {
        api.notifications().feedback(spectator, api.messages().get("spectator.nobody"));
        Sounds.MENU_DENY.play(spectator);
    }

    public boolean isPlaying(Player player) {
        return players.contains(player.getUniqueId());
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public List<Player> players() {
        return online(players);
    }

    public int playerCount() {
        return players.size();
    }

    public List<Player> spectators() {
        return online(spectators);
    }

    /** Vanished staff watch uncounted. */
    public int spectatorCount() {
        return (int) spectators().stream().filter(player -> !api.isVanished(player)).count();
    }

    /** Registers a countdown that the end of the round stops. */
    public Countdown timer(Countdown countdown) {
        timers.add(countdown);
        return countdown;
    }

    /**
     * Runs a timed phase of the round on {@code bar}'s seconds: the bar shows them unless the game's config turns
     * timer bars off, {@code onSecond} hears each remaining second and {@code onEnd} the end. The end of the round
     * stops it; a phase that ends early stops it through the returned countdown.
     */
    public Countdown clock(TimerBar bar, IntConsumer onSecond, Runnable onEnd) {
        if (config.timerBar()) {
            bars.add(bar);
            bar.update(bar.seconds());
        }
        return timer(Countdown.start(plugin, bar.seconds(), remaining -> {
            if (config.timerBar()) {
                bar.update(remaining);
            }
            onSecond.accept(remaining);
        }, () -> {
            bar.hide();
            onEnd.run();
        }));
    }

    /** Whether no boss bar tells the time, so that the sidebar has to. */
    public boolean clockOnSidebar() {
        return !config.timerBar();
    }

    /** Skips the respawn screen {@code delayTicks} after a death. */
    public void respawn(Player player, long delayTicks) {
        Tasks.later(plugin, delayTicks, () -> {
            if (player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

    /** Every player gets {@code tracker}, {@code minutes} into the final phase. */
    public void handOutTrackers(int minutes, ItemStack tracker) {
        arena.announceTrackers(minutes);
        for (var player : players()) {
            player.getInventory().addItem(tracker.clone());
            Sounds.NOTIFY.play(player);
        }
    }

    /** Called with every remaining second of the final phase; announces its last minutes in chat. */
    public void announceEnd(int remaining) {
        if (END_ANNOUNCEMENTS.contains(remaining)) {
            arena.announceEnd(remaining / SECONDS_PER_MINUTE);
        }
    }

    /**
     * Stops the round's countdowns and bars, shows the summary and restarts the server after {@code countdown.restart};
     * the winners must be awarded before. False if the round had already ended.
     */
    public boolean end() {
        return end(config.restartSeconds());
    }

    /** As {@link #end()}, with a longer wait before the restart for a game that still has something to offer. */
    public boolean end(int restartSeconds) {
        if (phase == GamePhase.ENDING) {
            return false;
        }
        enter(GamePhase.ENDING);
        stop();
        summary.show();
        timer(ServerRestart.start(plugin, arena, restartSeconds, summary::closeRespects));
        return true;
    }

    public void stop() {
        timers.forEach(Countdown::cancel);
        bars.forEach(TimerBar::hide);
    }

    private static List<Player> online(Set<UUID> ids) {
        return ids.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
    }
}
