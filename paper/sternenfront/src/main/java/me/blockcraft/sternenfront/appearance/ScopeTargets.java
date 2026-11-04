package me.blockcraft.sternenfront.appearance;

import fr.skytasul.glowingentities.GlowingEntities;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.RoundPhaseChangeEvent;
import me.blockcraft.sternenfront.game.LineOfSight;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("deprecation")
public final class ScopeTargets implements Listener, AutoCloseable {

    private final SternenfrontGame game;
    private final Optional<BodyRigs> bodies;
    private final GlowingEntities glowing;
    private final Map<UUID, Map<Entity, ChatColor>> shown = new HashMap<>();
    private final BukkitTask task;
    private final double range;
    private final double cosine;

    public ScopeTargets(Plugin plugin, SternenfrontGame game, Optional<BodyRigs> bodies) {
        this.game = game;
        this.bodies = bodies;
        range = Math.clamp(plugin.getConfig().getDouble("scope.targets.range", 160), 1, 256);
        cosine = Math.cos(Math.toRadians(Math.clamp(plugin.getConfig().getDouble("scope.targets.angle", 40), 1, 120) / 2));
        glowing = new GlowingEntities(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    static boolean inView(Vector facing, Vector offset, double range, double cosine) {
        double distance = offset.length();
        return distance > 0 && distance <= range && facing.dot(offset) / distance >= cosine;
    }

    private void tick() {
        var actors = game.combatants();
        for (var viewer : game.plugin().getServer().getOnlinePlayers()) {
            var wanted = new HashMap<Entity, ChatColor>();
            if (game.scoped(viewer)) {
                var eye = viewer.getEyeLocation();
                var faction = game.factionOf(viewer);
                for (var actor : actors) {
                    if (!actor.equals(viewer) && actor.isValid() && actor.getWorld().equals(eye.getWorld())
                            && (!(actor instanceof Player player) || !BlockcraftApi.get().isVanished(player)) && visible(eye, actor, range, cosine)) {
                        var colour = game.factionOf(actor).equals(faction) ? ChatColor.AQUA : ChatColor.RED;
                        var parts = bodies.map(rigs -> rigs.outlineParts(actor, viewer))
                                .orElseGet(() -> viewer.canSee(actor) ? List.of(actor) : List.of());
                        parts.forEach(part -> wanted.put(part, colour));
                    }
                }
            }
            update(viewer, wanted);
        }
    }

    static boolean visible(Location eye, LivingEntity actor, double range, double cosine) {
        var centre = actor.getBoundingBox().getCenter().subtract(eye.toVector());
        var head = actor.getEyeLocation().toVector().subtract(eye.toVector());
        return inView(eye.getDirection(), centre, range, cosine) && LineOfSight.clear(eye, centre)
                || inView(eye.getDirection(), head, range, cosine) && LineOfSight.clear(eye, head);
    }

    private void update(Player viewer, Map<Entity, ChatColor> wanted) {
        var previous = shown.getOrDefault(viewer.getUniqueId(), Map.of());
        try {
            for (var entity : previous.keySet()) {
                if (!wanted.containsKey(entity)) {
                    glowing.unsetGlowing(entity, viewer);
                    restoreTeam(viewer, entity);
                }
            }
            for (var entry : wanted.entrySet()) {
                if (entry.getValue() != previous.get(entry.getKey())) {
                    glowing.setGlowing(entry.getKey(), viewer, entry.getValue());
                }
            }
            if (wanted.isEmpty()) {
                shown.remove(viewer.getUniqueId());
            } else {
                shown.put(viewer.getUniqueId(), wanted);
            }
        } catch (ReflectiveOperationException e) {
            game.plugin().getSLF4JLogger().error("Could not update scoped target outlines", e);
        }
    }

    private static void restoreTeam(Player viewer, Entity entity) {
        if (entity instanceof Player player) {
            var team = viewer.getScoreboard().getEntryTeam(player.getName());
            if (team != null) {
                team.removeEntry(player.getName());
                team.addEntry(player.getName());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shown.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPhase(RoundPhaseChangeEvent event) {
        if (event.phase() != GamePhase.INGAME) {
            clear();
        }
    }

    private void clear() {
        game.plugin().getServer().getOnlinePlayers().forEach(viewer -> update(viewer, Map.of()));
        shown.clear();
    }

    @Override
    public void close() {
        task.cancel();
        HandlerList.unregisterAll(this);
        clear();
        glowing.disable();
    }
}
