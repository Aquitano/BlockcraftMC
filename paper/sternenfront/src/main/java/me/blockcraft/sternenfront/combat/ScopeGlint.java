package me.blockcraft.sternenfront.combat;

import me.blockcraft.sternenfront.game.LineOfSight;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A long rifle's scope catches the light: a player looking through one, or a bot with one on a target, glints at the
 * scope every so often for each enemy player within range who faces it with a clear view, so a sniper can be spotted.
 * Each of them gets a display of their own, sized by distance so it stays a few pixels across.
 */
public final class ScopeGlint {

    /** {@code presentation.scope-glint}. */
    public record Settings(boolean enabled, double range, int intervalTicks, int ticks, double viewDegrees, double size,
                           boolean bots) {

        public static Settings from(ConfigurationSection config) {
            return new Settings(config.getBoolean("presentation.scope-glint.enabled"),
                    Math.clamp(config.getDouble("presentation.scope-glint.range"), 8, 128),
                    Math.clamp(config.getInt("presentation.scope-glint.interval-ticks"), 5, 200),
                    Math.clamp(config.getInt("presentation.scope-glint.ticks"), 2, 20),
                    Math.clamp(config.getDouble("presentation.scope-glint.view-degrees"), 5, 90),
                    Math.clamp(config.getDouble("presentation.scope-glint.size"), 0.001, 0.2),
                    config.getBoolean("presentation.scope-glint.bots"));
        }
    }

    private static final String MODEL = "sternenfront/fx/glint";
    /** The front lens, ahead of the eyes along the look. */
    private static final double SCOPE_AHEAD = 0.45;
    private static final double NEAREST = 2;
    private static final float SMALLEST = 0.3f;
    /** Towards each viewer from the lens, so the sniper's own head and rifle do not cover the glint. */
    private static final double TOWARDS_VIEWER = 0.35;
    /** A display's view range counts in 64-block steps, which the client scales by its entity distance setting. */
    private static final double VIEW_RANGE_BLOCKS = 48;
    /** Ticks from raising the scope to its first glint, so a quick look does not flash. */
    private static final int FIRST_GLINT_TICKS = 6;

    private final SternenfrontGame game;
    private final Settings settings;
    private final double facing;
    private final Map<UUID, Long> nextGlint = new HashMap<>();
    private final Set<UUID> sighting = new HashSet<>();

    public ScopeGlint(SternenfrontGame game, Settings settings) {
        this.game = game;
        this.settings = settings;
        facing = Math.cos(Math.toRadians(settings.viewDegrees()));
    }

    public void tick(long tick) {
        if (!settings.enabled()) {
            return;
        }
        sighting.clear();
        for (var sniper : game.combatants()) {
            var look = look(sniper, tick);
            if (look.isEmpty()) {
                continue;
            }
            var id = sniper.getUniqueId();
            sighting.add(id);
            long next = nextGlint.computeIfAbsent(id, ignored -> tick + FIRST_GLINT_TICKS);
            if (tick >= next) {
                nextGlint.put(id, tick + settings.intervalTicks());
                glint(sniper, look.get());
            }
        }
        nextGlint.keySet().retainAll(sighting);
    }

    private Optional<Vector> look(LivingEntity sniper, long tick) {
        if (sniper instanceof Player player) {
            return game.scoped(player) ? Optional.of(player.getEyeLocation().getDirection()) : Optional.empty();
        }
        return settings.bots() ? game.bots().sighting(sniper, tick) : Optional.empty();
    }

    private void glint(LivingEntity sniper, Vector look) {
        var side = game.factionOf(sniper);
        if (side.isEmpty()) {
            return;
        }
        var scope = sniper.getEyeLocation().add(look.clone().multiply(SCOPE_AHEAD));
        for (var fighter : game.combatants()) {
            if (!(fighter instanceof Player viewer) || game.factionOf(viewer).filter(side.get().enemy()::equals).isEmpty()) {
                continue;
            }
            var eye = viewer.getEyeLocation();
            if (!eye.getWorld().equals(scope.getWorld())) {
                continue;
            }
            var toScope = scope.toVector().subtract(eye.toVector());
            double distance = toScope.length();
            // The lens faces along the look, so a viewer behind the sniper sees none.
            if (distance < NEAREST || distance > settings.range() || look.dot(toScope) >= 0
                    || eye.getDirection().dot(toScope) < facing * distance || !LineOfSight.clear(eye, toScope)) {
                continue;
            }
            float scale = Math.max(SMALLEST, (float) (distance * settings.size()));
            var shown = scope.clone().subtract(toScope.clone().multiply(TOWARDS_VIEWER / distance));
            game.effects().flares().twinkle(MODEL, shown, scale, settings.ticks(), display -> {
                display.setViewRange((float) Math.max(1, distance / VIEW_RANGE_BLOCKS));
                display.setVisibleByDefault(false);
                viewer.showEntity(game.plugin(), display);
            });
        }
    }

    public void clear() {
        nextGlint.clear();
    }
}
