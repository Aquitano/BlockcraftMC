package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.vanish.VanishChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The bookkeeping every game's round shares, held by the game next to its own rules. It seats every joiner: while
 * the lobby is open and has room, they play, unless they are vanished; everyone else watches, and a vanished
 * spectator is neither announced nor counted. A player who vanishes while the lobby waits moves to the spectators
 * the same way. It tells the proxy the phase, owns the countdowns and boss bars that
 * end with the round, counts a mid-round quit as a death and a loss, and ends the round once.
 */
public final class Round implements Listener {

    private static final Set<Integer> END_ANNOUNCEMENTS = Set.of(600, 300, 180, 60);
    private static final int SECONDS_PER_MINUTE = 60;

    private final Plugin plugin;
    private final BlockcraftApi api;
    private final Arena arena;
    private final RoundSummary summary;
    private final int capacity;
    private final Set<UUID> players = new LinkedHashSet<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final List<Countdown> timers = new ArrayList<>();
    private final List<TimerBar> bars = new ArrayList<>();
    private GamePhase phase = GamePhase.LOBBY;

    public Round(Plugin plugin, Arena arena, RoundSummary summary, int capacity) {
        this.plugin = plugin;
        this.api = BlockcraftApi.get();
        this.arena = arena;
        this.summary = summary;
        this.capacity = capacity;
        Bukkit.getPluginManager().registerEvents(this, plugin);
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

    /** Open menus and dialogs close, because what they offered may be gone, and the proxy hears the new state. */
    public void enter(GamePhase phase) {
        this.phase = phase;
        Bukkit.getOnlinePlayers().forEach(player -> {
            Menu.close(player);
            player.closeDialog();
        });
        api.presence().state(phase.serverState());
    }

    /** Seats a joiner and returns whether they play; a spectator is in spectator mode already. */
    public boolean join(Player player) {
        if (phase != GamePhase.LOBBY || players.size() >= capacity || api.isVanished(player)) {
            spectators.add(player.getUniqueId());
            PlayerReset.spectate(player);
            arena.arrive(player);
            return false;
        }
        players.add(player.getUniqueId());
        summary.join(player);
        arena.welcome(player, players.size(), capacity);
        return true;
    }

    /** Returns whether the player was playing; leaving a running round counts as a death and a loss. */
    public boolean leave(Player player) {
        var id = player.getUniqueId();
        spectators.remove(id);
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
        spectators.add(player.getUniqueId());
        PlayerReset.spectate(player);
        arena.farewell(player, players.size(), capacity);
    }

    /** Out for good: the player watches the rest, which the game shows once they have respawned. */
    public void eliminate(Player player) {
        players.remove(player.getUniqueId());
        spectators.add(player.getUniqueId());
        summary.eliminate(player);
    }

    /** The spectator came to watch a friend, which the proxy names only after the join. */
    public void follow(Player spectator, UUID friend) {
        if (!isSpectator(spectator)) {
            return;
        }
        var target = Bukkit.getPlayer(friend);
        if (target != null && spectator.canSee(target)) {
            arena.follow(spectator, target);
        }
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

    /** Registers a boss bar that the end of the round hides, as leaving hides it from the leaver. */
    public TimerBar bar(TimerBar bar) {
        bars.add(bar);
        return bar;
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
     * Stops the round's countdowns and bars, shows the summary and restarts the server after {@code restartSeconds};
     * the winners must be awarded before. False if the round had already ended.
     */
    public boolean end(int restartSeconds) {
        if (phase == GamePhase.ENDING) {
            return false;
        }
        enter(GamePhase.ENDING);
        stop();
        summary.show();
        timer(ServerRestart.start(plugin, arena, restartSeconds));
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
