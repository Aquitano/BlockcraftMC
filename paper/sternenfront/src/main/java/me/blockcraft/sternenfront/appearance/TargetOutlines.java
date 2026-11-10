package me.blockcraft.sternenfront.appearance;

import fr.skytasul.glowingentities.GlowingEntities;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.RoundPhaseChangeEvent;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("deprecation")
public final class TargetOutlines implements Listener, AutoCloseable {

    public enum Outline { ALLY, ENEMY }

    /**
     * Who sees whom outlined: a fighter on the field with the pack sees enemies within {@code range}, and allies too with
     * {@code allies}. The {@code outlines} section of config.yml; the body rigs choose a model by the same rule.
     */
    public record Rule(SternenfrontGame game, boolean enabled, double range, boolean allies) {

        public static Rule from(SternenfrontGame game) {
            var config = game.plugin().getConfig();
            return new Rule(game, config.getBoolean("outlines.enabled"), Math.clamp(config.getDouble("outlines.range"), 1, 256),
                    config.getBoolean("outlines.allies"));
        }

        public Optional<Outline> outline(Player viewer, LivingEntity actor) {
            var api = BlockcraftApi.get();
            var faction = game.factionOf(viewer);
            if (!enabled || actor.equals(viewer) || faction.isEmpty() || !actor.isValid() || !game.onField(viewer)
                    || !actor.getWorld().equals(viewer.getWorld()) || !api.resourcePack().applied(viewer)
                    || actor instanceof Player player && api.isVanished(player) || !inRange(viewer.getEyeLocation(), actor, range)) {
                return Optional.empty();
            }
            return game.factionOf(actor).equals(faction) ? Optional.of(Outline.ALLY).filter(ignored -> allies) : Optional.of(Outline.ENEMY);
        }
    }

    private final SternenfrontGame game;
    private final Optional<BodyRigs> bodies;
    private final Rule rule;
    private final GlowingEntities glowing;
    private final Map<UUID, Map<Entity, ChatColor>> shown = new HashMap<>();
    private final BukkitTask task;

    public TargetOutlines(Plugin plugin, SternenfrontGame game, Optional<BodyRigs> bodies) {
        this.game = game;
        this.bodies = bodies;
        rule = Rule.from(game);
        glowing = new GlowingEntities(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        var actors = game.combatants();
        for (var viewer : game.plugin().getServer().getOnlinePlayers()) {
            var wanted = new HashMap<Entity, ChatColor>();
            for (var actor : actors) {
                rule.outline(viewer, actor).ifPresent(outline -> {
                    var colour = outline == Outline.ALLY ? ChatColor.DARK_AQUA : ChatColor.DARK_RED;
                    var parts = bodies.map(rigs -> rigs.outlineParts(actor, viewer))
                            .orElseGet(() -> viewer.canSee(actor) ? List.of(actor) : List.of());
                    parts.forEach(part -> wanted.put(part, colour));
                });
            }
            update(viewer, wanted);
        }
    }

    static boolean inRange(Location eye, LivingEntity actor, double range) {
        return actor.getBoundingBox().getCenter().subtract(eye.toVector()).lengthSquared() <= range * range;
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
            game.plugin().getSLF4JLogger().error("Could not update target outlines", e);
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
