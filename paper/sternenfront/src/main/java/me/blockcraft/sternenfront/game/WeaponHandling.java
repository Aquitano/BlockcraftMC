package me.blockcraft.sternenfront.game;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.common.config.StrictConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * How a blaster behaves in the hands: aim opens up on the move, in the air and with a hot barrel, tightens at the sights
 * and settles to nothing in a long rifle's scope, a sprint keeps the gun down for a moment after it ends, and damage thins
 * out over the far part of a weapon's range.
 * The {@code combat.handling} section of config.yml, whose {@code blasters} override it per blaster; a blaster without
 * its own takes its profile's.
 */
public record WeaponHandling(Map<Blaster, Factors> blasters, double falloffFrom, double falloffMinimum) {

    /**
     * Spread multiplies by {@code moving}, {@code aiming} and {@code airborne} and grows by {@code hot} times the heat;
     * in a scope it shrinks to nothing over {@code settleTicks}; the gun stays down for {@code sprintRecoveryTicks}
     * after a sprint.
     */
    public record Factors(double moving, double aiming, double airborne, double hot, int settleTicks, int sprintRecoveryTicks) {

        static Factors from(StrictConfig section) {
            return new Factors(Math.max(1, section.decimal("moving")), Math.clamp(section.decimal("aiming"), 0, 1),
                    Math.max(1, section.decimal("airborne")), Math.max(0, section.decimal("hot")),
                    Math.max(1, section.integer("settle-ticks")), Math.max(0, section.integer("sprint-recovery-ticks")));
        }
    }

    /** How the shooter stands, and for how many ticks they have looked through a scope, if they do. */
    public record Posture(boolean moving, boolean aiming, boolean airborne, OptionalInt scopedTicks) {

        public static Posture of(Player shooter, boolean aiming, OptionalInt scopedTicks) {
            var input = shooter.getCurrentInput();
            // Player deprecates isOnGround as client-reported; through Entity it is the same call without the warning.
            Entity body = shooter;
            return new Posture(input.isForward() || input.isBackward() || input.isLeft() || input.isRight(), aiming,
                    !body.isOnGround() && !shooter.isInsideVehicle(), scopedTicks);
        }

        public static Posture walking(boolean moving) {
            return new Posture(moving, false, false, OptionalInt.empty());
        }
    }

    /** A blaster in {@code blasters}, or else its profile there, overrides the keys it sets for itself. */
    public static WeaponHandling from(ConfigurationSection section) {
        var handling = BundledDefaults.strict(section);
        var overrides = handling.section("blasters");
        var blasters = new EnumMap<Blaster, Factors>(Blaster.class);
        for (var blaster : Blaster.values()) {
            var layered = new LinkedHashMap<>(handling.values());
            var key = overrides.has(blaster.key()) ? blaster.key() : blaster.profile().key();
            if (overrides.has(key)) {
                layered.putAll(overrides.section(key).values());
            }
            blasters.put(blaster, Factors.from(new StrictConfig(handling.file(), handling.path(), layered)));
        }
        return new WeaponHandling(Map.copyOf(blasters), Math.clamp(handling.decimal("falloff-from"), 0, 1),
                Math.clamp(handling.decimal("falloff-minimum"), 0, 1));
    }

    public double spread(Blaster blaster, double base, Posture posture, double heat) {
        var factors = blasters.get(blaster);
        double unsettled = posture.scopedTicks().stream().mapToDouble(ticks -> 1 - Math.min(1, ticks / (double) factors.settleTicks()))
                .findFirst().orElse(1);
        return base * (posture.moving() ? factors.moving() : 1) * (posture.aiming() ? factors.aiming() : 1)
                * (posture.airborne() ? factors.airborne() : 1) * (1 + Math.clamp(heat, 0, 1) * factors.hot()) * unsettled;
    }

    /** Whether a gun whose holder last sprinted at {@code sprintedAt} is still coming up at {@code tick}. */
    public boolean lowered(Blaster blaster, long sprintedAt, long tick) {
        return tick - sprintedAt <= sprintRecoveryTicks(blaster);
    }

    public int sprintRecoveryTicks(Blaster blaster) {
        return blasters.get(blaster).sprintRecoveryTicks();
    }

    public double damage(double base, double travelled, double range) {
        double start = range * falloffFrom;
        if (travelled <= start || range <= start) {
            return base;
        }
        double share = Math.min(1, (travelled - start) / (range - start));
        return base * (1 - share * (1 - falloffMinimum));
    }
}
