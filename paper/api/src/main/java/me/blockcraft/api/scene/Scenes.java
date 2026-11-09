package me.blockcraft.api.scene;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.presentation.PresentationScope;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Plays a {@link SceneTimeline} through a private display entity the player spectates.
 * <p>
 * A client takes a spectator target only if it already knows that entity, so a camera is attached once the player
 * tracks it, and the timeline's clock starts then. While the client is still loading a world it joined or was sent
 * to, even by the caller just before the scene, the first camera waits: a display spawned in the tick of such a
 * teleport joins the world late, and showing it then has no effect. Attaching can make the server drop and resend the
 * camera, after which the client still looks through the dropped copy, so a camera that was untracked is attached again.
 * <p>
 * The display eases every move over {@link #SMOOTHING} ticks, which hides uneven server ticks and the coarse rotation
 * steps of entity movement packets, and the camera aims that far ahead to make up for the lag. A cut swaps to a second
 * display prepared at the new shot, because a teleport of the same display would glide there.
 */
public final class Scenes implements Listener, AutoCloseable {
    public enum End { COMPLETED, SKIPPED, CANCELLED, DISCONNECTED, TELEPORTED, FAILED }
    static final int SMOOTHING = 4;
    private static final int LEAD = SMOOTHING - 1;
    private static final int PREPARE_TICKS = 10;
    /** A client still loading a world it was just sent to can take seconds before it knows the camera. */
    private static final int ATTACH_TIMEOUT_TICKS = 200;
    private static final Set<UUID> VIEWING = new HashSet<>();
    /** Players whose client has not yet finished loading the world they joined or changed to. */
    private static final Set<UUID> LOADING = new HashSet<>();
    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final BukkitTask task;
    private boolean changing;
    private boolean closed;

    public Scenes(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public static boolean isViewing(Player player) {
        return VIEWING.contains(player.getUniqueId());
    }

    public boolean active(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean play(Player player, SceneTimeline timeline, Consumer<End> completion) {
        return play(player, timeline, new PresentationScope(plugin), completion);
    }

    public boolean play(Player player, SceneTimeline timeline, PresentationScope resources, Consumer<End> completion) {
        boolean started = false;
        try {
            started = start(player, timeline, resources, completion);
            return started;
        } catch (RuntimeException failure) {
            plugin.getSLF4JLogger().warn("Could not prepare scene", failure);
            return false;
        } finally {
            if (!started) resources.close();
        }
    }

    private boolean start(Player player, SceneTimeline timeline, PresentationScope resources, Consumer<End> completion) {
        if (resources.closed()) return false;
        if (closed || isViewing(player) || player.isDead() || !player.isOnline() || player.isInsideVehicle()) return false;
        var api = BlockcraftApi.get();
        if (api.dialogues().active(player) || api.visuals().active(player)) {
            player.sendMessage(api.messages().get("presentation.busy"));
            return false;
        }
        api.visuals().clearAccent(player);
        var saved = Saved.capture(player);
        var at = timeline.camera().sample(0).in(timeline.world());
        var session = new Session(player, timeline, completion, saved, resources, new PresentationScope(plugin));
        session.loading = LOADING.contains(player.getUniqueId()) || !player.getWorld().equals(at.getWorld());
        sessions.put(player.getUniqueId(), session);
        VIEWING.add(player.getUniqueId());
        try {
            changing = true;
            player.closeInventory();
            player.setGameMode(GameMode.SPECTATOR);
            player.setInvulnerable(true);
            if (!player.teleport(at)) {
                finish(session, End.FAILED, true);
                return false;
            }
            if (!session.loading) prepare(session, timeline.camera().sample(0), 0);
            session.conceal(JavaPlugin.getProvidingPlugin(Scenes.class));
            render(session);
            return true;
        } catch (RuntimeException failure) {
            plugin.getSLF4JLogger().warn("Could not start scene", failure);
            finish(session, End.FAILED, true);
            return false;
        } finally {
            changing = false;
        }
    }

    static ItemDisplay spawnCamera(Location at) {
        return at.getWorld().spawn(at, ItemDisplay.class, camera -> {
            camera.setItemStack(null);
            camera.setGravity(false);
            camera.setInvulnerable(true);
            camera.setPersistent(false);
            camera.setVisibleByDefault(false);
            camera.setShadowRadius(0);
            camera.setShadowStrength(0);
            camera.setTeleportDuration(SMOOTHING);
        });
    }

    public void stop(Player player) {
        var session = sessions.get(player.getUniqueId());
        if (session != null) finish(session, End.CANCELLED, true);
    }

    public void skip(Player player) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || session.skipped) return;
        session.skipped = true;
        if (session.timeline.skipMode() == SceneTimeline.SkipMode.COMPLETE) {
            finish(session, End.SKIPPED, true);
            return;
        }
        session.resources.close();
        var camera = session.timeline.camera();
        if (session.camera != null && camera.shotEnd(session.tick) <= camera.duration()) {
            prepare(session, camera.sample(camera.duration()), session.tick);
        }
    }

    private void tick() {
        for (var session : List.copyOf(sessions.values())) {
            try {
                if (!session.player.isOnline()) {
                    finish(session, End.DISCONNECTED, true);
                } else if (session.player.isDead() || session.player.getGameMode() != GameMode.SPECTATOR
                        || session.camera != null && !session.camera.isValid()) {
                    finish(session, End.CANCELLED, true);
                } else {
                    if (session.camera != null) session.tick++;
                    render(session);
                    if (session.tick >= session.timeline.camera().duration()) {
                        finish(session, session.skipped ? End.SKIPPED : End.COMPLETED, true);
                    }
                }
            } catch (RuntimeException failure) {
                plugin.getSLF4JLogger().warn("Scene failed", failure);
                finish(session, End.FAILED, true);
            }
        }
    }

    private void render(Session session) {
        var timeline = session.timeline;
        var path = timeline.camera();
        if (session.pending != null && session.tick >= session.swapAt
                && (session.pending.isTrackedBy(session.player) && session.waited > 0 || session.waited >= ATTACH_TIMEOUT_TICKS)) {
            swap(session);
        } else if (session.pending != null) {
            session.waited++;
        } else if (session.camera == null && (!session.loading || ++session.waited >= ATTACH_TIMEOUT_TICKS)) {
            prepare(session, path.sample(0), 0);
        }
        if (session.camera != null && session.untracked && session.camera.isTrackedBy(session.player)) {
            session.untracked = false;
            attach(session, null);
            attach(session, session.camera);
        }
        session.hideHud();
        if (session.camera == null) {
            timeline.frame().show(session.player, 0, path.duration(), session.skipped);
            return;
        }
        int shownTick = session.skipped ? path.duration() : session.tick;
        int shotEnd = path.shotEnd(shownTick);
        if (session.pending == null && !session.skipped && shotEnd <= path.duration() && shotEnd - shownTick <= PREPARE_TICKS) {
            prepare(session, path.sample(shotEnd), shotEnd);
        }
        session.camera.teleport(path.sample(Math.min(shownTick + LEAD, Math.min(shotEnd - 1, path.duration()))).in(timeline.world()));
        if (session.player.getSpectatorTarget() != session.camera) attach(session, session.camera);
        while (!session.skipped && session.nextCue < timeline.cues().size() && timeline.cues().get(session.nextCue).tick() <= session.tick) {
            timeline.cues().get(session.nextCue++).action().run();
        }
        timeline.frame().show(session.player, session.tick, timeline.camera().duration(), session.skipped);
    }

    private void prepare(Session session, CameraPath.Pose pose, int swapAt) {
        var camera = spawnCamera(pose.in(session.timeline.world()));
        session.prepare(camera, swapAt);
        session.player.showEntity(plugin, camera);
    }

    private void swap(Session session) {
        var previous = session.camera;
        session.camera = session.pending;
        session.pending = null;
        session.waited = 0;
        session.untracked = !session.camera.isTrackedBy(session.player);
        attach(session, session.camera);
        if (previous != null) previous.remove();
    }

    private void attach(Session session, Entity camera) {
        boolean previousChanging = changing;
        changing = true;
        try {
            session.player.setSpectatorTarget(camera);
        } finally {
            changing = previousChanging;
        }
    }

    private void finish(Session session, End reason, boolean returnToStart) {
        if (!sessions.remove(session.player.getUniqueId(), session)) return;
        VIEWING.remove(session.player.getUniqueId());
        boolean previousChanging = changing;
        changing = true;
        try {
            session.saved.restore(session.player, returnToStart);
        } catch (RuntimeException failure) {
            plugin.getSLF4JLogger().warn("Could not restore scene participant", failure);
        } finally {
            session.reveal();
            session.cameraResources.close();
            session.resources.close();
            changing = previousChanging;
        }
        try {
            session.completion.accept(reason);
        } catch (RuntimeException failure) {
            plugin.getSLF4JLogger().warn("Scene completion failed", failure);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        LOADING.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUntrack(PlayerUntrackEntityEvent event) {
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && event.getEntity().equals(session.camera)) session.untracked = true;
    }

    @EventHandler
    public void onLoaded(PlayerClientLoadedWorldEvent event) {
        LOADING.remove(event.getPlayer().getUniqueId());
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) session.loading = false;
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        if (event.getInput().isSneak()) skip(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDetach(PlayerStopSpectatingEntityEvent event) {
        if (!changing && active(event.getPlayer())) {
            event.setCancelled(true);
            skip(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (changing) return;
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) finish(session, End.TELEPORTED, false);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        LOADING.add(event.getPlayer().getUniqueId());
        if (changing) return;
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) finish(session, End.TELEPORTED, false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) finish(session, End.CANCELLED, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        LOADING.remove(event.getPlayer().getUniqueId());
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) finish(session, End.DISCONNECTED, true);
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) close();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        task.cancel();
        for (var session : List.copyOf(sessions.values())) finish(session, End.CANCELLED, true);
        HandlerList.unregisterAll(this);
    }

    record Saved(Location location, GameMode mode, boolean allowFlight, boolean flying,
                         boolean invulnerable, boolean collidable, float fallDistance, Entity target) {
        static Saved capture(Player player) {
            return new Saved(player.getLocation(), player.getGameMode(), player.getAllowFlight(), player.isFlying(),
                    player.isMarkedInvulnerable(), player.isCollidable(), player.getFallDistance(),
                    player.getGameMode() == GameMode.SPECTATOR ? player.getSpectatorTarget() : null);
        }

        void restore(Player player, boolean returnToStart) {
            if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
            player.setGameMode(mode);
            player.setAllowFlight(allowFlight);
            player.setFlying(flying && allowFlight);
            player.setInvulnerable(invulnerable);
            player.setCollidable(collidable);
            player.setFallDistance(fallDistance);
            if (returnToStart) player.teleport(location);
            if (returnToStart && mode == GameMode.SPECTATOR && target != null && target.isValid()) player.setSpectatorTarget(target);
        }
    }

    private static final class Session {
        final Player player;
        final SceneTimeline timeline;
        final Consumer<End> completion;
        final Saved saved;
        final PresentationScope resources;
        final PresentationScope cameraResources;
        final List<BossBar> hiddenBars = new ArrayList<>();
        final List<Player> hiddenPlayers = new ArrayList<>();
        Plugin hider;
        String hiddenSidebar;
        ItemDisplay camera;
        ItemDisplay pending;
        int swapAt;
        int waited;
        /** Attached before the client knew the camera, or the camera was dropped since: attached again once it is back. */
        boolean untracked;
        boolean loading;
        int tick;
        int nextCue;
        boolean skipped;

        Session(Player player, SceneTimeline timeline, Consumer<End> completion, Saved saved,
                PresentationScope resources, PresentationScope cameraResources) {
            this.player = player;
            this.timeline = timeline;
            this.completion = completion;
            this.saved = saved;
            this.resources = resources;
            this.cameraResources = cameraResources;
        }

        void prepare(ItemDisplay next, int at) {
            if (pending != null) pending.remove();
            pending = next;
            swapAt = at;
            waited = 0;
            cameraResources.own(next::remove);
        }

        /**
         * Boss bars, the sidebar and other players would sit in the shot. The game keeps owning them and may show them
         * again at any time, as a countdown does every second, so the HUD is hidden every tick and given back at the end.
         */
        void conceal(Plugin hider) {
            this.hider = hider;
            hideHud();
            for (var other : player.getServer().getOnlinePlayers()) {
                if (other != player && player.canSee(other)) {
                    player.hidePlayer(hider, other);
                    hiddenPlayers.add(other);
                }
            }
        }

        void hideHud() {
            var shown = new ArrayList<BossBar>();
            player.activeBossBars().forEach(shown::add);
            for (var bar : shown) {
                player.hideBossBar(bar);
                if (!hiddenBars.contains(bar)) hiddenBars.add(bar);
            }
            var sidebar = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
            if (sidebar != null) {
                hiddenSidebar = sidebar.getName();
                sidebar.setDisplaySlot(null);
            }
        }

        void reveal() {
            hiddenBars.forEach(player::showBossBar);
            hiddenBars.clear();
            var board = player.getScoreboard();
            var sidebar = hiddenSidebar == null ? null : board.getObjective(hiddenSidebar);
            if (sidebar != null && board.getObjective(DisplaySlot.SIDEBAR) == null) sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
            hiddenSidebar = null;
            for (var other : hiddenPlayers) {
                if (other.isOnline()) player.showPlayer(hider, other);
            }
            hiddenPlayers.clear();
        }
    }
}
