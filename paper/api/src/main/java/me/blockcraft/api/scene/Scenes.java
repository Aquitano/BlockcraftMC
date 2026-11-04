package me.blockcraft.api.scene;

import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class Scenes implements Listener, AutoCloseable {
    public enum End { COMPLETED, SKIPPED, CANCELLED, DISCONNECTED, TELEPORTED, FAILED }
    private static final Set<UUID> VIEWING = new HashSet<>();
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
        if (closed || isViewing(player) || player.isDead() || !player.isOnline() || player.isInsideVehicle()) return false;
        var api = BlockcraftApi.get();
        if (api.dialogues().active(player) || api.visuals().active(player)) {
            player.sendMessage(api.messages().get("presentation.busy"));
            return false;
        }
        api.visuals().clearAccent(player);
        var saved = Saved.capture(player);
        var at = timeline.camera().sample(0).in(timeline.world());
        ItemDisplay camera;
        try {
            camera = spawnCamera(at);
        } catch (RuntimeException failure) {
            plugin.getSLF4JLogger().warn("Could not create scene camera", failure);
            return false;
        }
        var session = new Session(player, timeline, completion, saved, camera);
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
            player.showEntity(plugin, camera);
            player.setSpectatorTarget(camera);
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
            camera.setTeleportDuration(1);
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
        if (session.timeline.skipMode() == SceneTimeline.SkipMode.COMPLETE) finish(session, End.SKIPPED, true);
    }

    private void tick() {
        for (var session : List.copyOf(sessions.values())) {
            try {
                if (!session.player.isOnline()) {
                    finish(session, End.DISCONNECTED, true);
                } else if (session.player.getGameMode() != GameMode.SPECTATOR || !session.camera.isValid()) {
                    finish(session, End.CANCELLED, true);
                } else {
                    session.tick++;
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
        int shownTick = session.skipped ? timeline.camera().duration() : session.tick;
        boolean cut = timeline.camera().frames().stream().anyMatch(frame -> frame.tick() == shownTick && frame.cut());
        session.camera.setTeleportDuration(cut ? 0 : 1);
        session.camera.teleport(timeline.camera().sample(shownTick).in(timeline.world()));
        if (session.player.getSpectatorTarget() != session.camera) session.player.setSpectatorTarget(session.camera);
        while (session.nextCue < timeline.cues().size() && timeline.cues().get(session.nextCue).tick() <= session.tick) {
            timeline.cues().get(session.nextCue++).action().run();
        }
        timeline.frame().show(session.player, session.tick, timeline.camera().duration(), session.skipped);
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
            session.camera.remove();
            changing = previousChanging;
        }
        try {
            session.completion.accept(reason);
        } catch (RuntimeException failure) {
            plugin.getSLF4JLogger().warn("Scene completion failed", failure);
        }
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
        if (changing) return;
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) finish(session, End.TELEPORTED, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
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
        final ItemDisplay camera;
        int tick;
        int nextCue;
        boolean skipped;

        Session(Player player, SceneTimeline timeline, Consumer<End> completion, Saved saved, ItemDisplay camera) {
            this.player = player;
            this.timeline = timeline;
            this.completion = completion;
            this.saved = saved;
            this.camera = camera;
        }
    }
}
