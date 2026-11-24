package me.blockcraft.sternenfront.appearance;

import fr.skytasul.glowingentities.GlowingEntities;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.RoundPhaseChangeEvent;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;
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
    public static final class Rule {

        private record Viewer(World world, Faction faction, Vector eye) { }
        private record Actor(World world, Optional<Faction> faction, Vector centre) { }

        private final SternenfrontGame game;
        private final boolean enabled;
        private final double rangeSquared;
        private final boolean allies;
        private final Map<Player, Optional<Viewer>> viewers = new HashMap<>();
        private final Map<LivingEntity, Optional<Actor>> actors = new HashMap<>();
        private final Map<Player, Map<LivingEntity, Optional<Outline>>> animated = new HashMap<>();
        private int tick;

        private Rule(SternenfrontGame game, boolean enabled, double range, boolean allies) {
            this.game = game;
            this.enabled = enabled;
            rangeSquared = range * range;
            this.allies = allies;
        }

        public static Rule from(SternenfrontGame game) {
            var config = game.plugin().getConfig();
            return new Rule(game, config.getBoolean("outlines.enabled"), Math.clamp(config.getDouble("outlines.range"), 1, 256),
                    config.getBoolean("outlines.allies"));
        }

        private void begin() {
            int current = Bukkit.getCurrentTick();
            if (current != tick) {
                tick = current;
                viewers.clear();
                actors.clear();
                animated.clear();
            }
        }

        boolean eligible(Player player) {
            if (!enabled || game.phase() != GamePhase.INGAME) return false;
            begin();
            return viewers.computeIfAbsent(player, this::viewer).isPresent();
        }

        private Optional<Viewer> viewer(Player player) {
            if (!game.onField(player) || !BlockcraftApi.get().resourcePack().applied(player)) return Optional.empty();
            return game.factionOf(player).map(side -> new Viewer(player.getWorld(), side, player.getEyeLocation().toVector()));
        }

        private Optional<Actor> actor(LivingEntity entity) {
            if (!entity.isValid() || entity instanceof Player player && BlockcraftApi.get().isVanished(player)) {
                return Optional.empty();
            }
            return Optional.of(new Actor(entity.getWorld(), game.factionOf(entity), entity.getBoundingBox().getCenter()));
        }

        boolean animatedEnemy(Player viewer, LivingEntity actor) {
            return eligible(viewer) && animated.computeIfAbsent(viewer, ignored -> new HashMap<>())
                    .computeIfAbsent(actor, target -> decide(viewer, target)).orElse(null) == Outline.ENEMY;
        }

        private Optional<Outline> outline(Player viewer, LivingEntity actor) {
            var cached = animated.get(viewer);
            var outline = cached == null ? null : cached.get(actor);
            return outline == null ? decide(viewer, actor) : outline;
        }

        private Optional<Outline> decide(Player viewer, LivingEntity entity) {
            if (entity.equals(viewer)) return Optional.empty();
            var from = viewers.get(viewer).orElseThrow();
            var target = actors.computeIfAbsent(entity, this::actor);
            if (target.isEmpty()) return Optional.empty();
            var to = target.orElseThrow();
            if (!from.world().equals(to.world()) || from.eye().distanceSquared(to.centre()) > rangeSquared) {
                return Optional.empty();
            }
            return to.faction().filter(from.faction()::equals).isPresent()
                    ? allies ? Optional.of(Outline.ALLY) : Optional.empty()
                    : Optional.of(Outline.ENEMY);
        }
    }

    private final SternenfrontGame game;
    private final Optional<BodyRigs> bodies;
    private final Rule rule;
    private final GlowingEntities glowing;
    private static final class Shown {
        ChatColor colour;
        int tick;
    }

    private final Map<UUID, Map<Entity, Shown>> shown = new HashMap<>();
    private final BukkitTask task;

    public TargetOutlines(Plugin plugin, SternenfrontGame game, Optional<BodyRigs> bodies, Rule rule) {
        this.game = game;
        this.bodies = bodies;
        this.rule = rule;
        glowing = new GlowingEntities(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        if (game.phase() != GamePhase.INGAME) return;
        var actors = game.combatants();
        int tick = Bukkit.getCurrentTick();
        for (var viewer : game.plugin().getServer().getOnlinePlayers()) {
            try {
                if (rule.eligible(viewer)) {
                    for (var actor : actors) {
                        var outline = rule.outline(viewer, actor).orElse(null);
                        if (outline == null) continue;
                        var colour = outline == Outline.ALLY ? ChatColor.DARK_AQUA : ChatColor.DARK_RED;
                        var parts = bodies.map(rigs -> rigs.outlineParts(actor, viewer))
                                .orElseGet(() -> viewer.canSee(actor) ? List.of(actor) : List.of());
                        for (var part : parts) {
                            var mark = shown.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashMap<>())
                                    .computeIfAbsent(part, ignored -> new Shown());
                            if (mark.colour != colour) {
                                glowing.setGlowing(part, viewer, colour);
                                mark.colour = colour;
                            }
                            mark.tick = tick;
                        }
                    }
                }
                removeOld(viewer, tick, false);
            } catch (ReflectiveOperationException e) {
                game.plugin().getSLF4JLogger().error("Could not update target outlines", e);
            }
        }
    }

    static boolean inRange(Location eye, LivingEntity actor, double range) {
        return actor.getBoundingBox().getCenter().distanceSquared(eye.toVector()) <= range * range;
    }

    private void removeOld(Player viewer, int tick, boolean clear) throws ReflectiveOperationException {
        var marks = shown.get(viewer.getUniqueId());
        if (marks == null) return;
        var entries = marks.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (!clear && entry.getValue().tick == tick) continue;
            glowing.unsetGlowing(entry.getKey(), viewer);
            restoreTeam(viewer, entry.getKey());
            entries.remove();
        }
        if (marks.isEmpty()) shown.remove(viewer.getUniqueId());
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
        for (var viewer : game.plugin().getServer().getOnlinePlayers()) {
            try {
                removeOld(viewer, 0, true);
            } catch (ReflectiveOperationException e) {
                game.plugin().getSLF4JLogger().error("Could not clear target outlines", e);
            }
        }
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
