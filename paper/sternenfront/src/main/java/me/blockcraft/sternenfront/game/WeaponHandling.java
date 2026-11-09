package me.blockcraft.sternenfront.game;

import me.blockcraft.api.item.HotbarItem;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.EnumMap;
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

        static Factors from(ConfigurationSection section, Factors fallback) {
            return new Factors(Math.max(1, section.getDouble("moving", fallback.moving())),
                    Math.clamp(section.getDouble("aiming", fallback.aiming()), 0, 1),
                    Math.max(1, section.getDouble("airborne", fallback.airborne())), Math.max(0, section.getDouble("hot", fallback.hot())),
                    Math.max(1, section.getInt("settle-ticks", fallback.settleTicks())),
                    Math.max(0, section.getInt("sprint-recovery-ticks", fallback.sprintRecoveryTicks())));
        }
    }

    /** How the shooter stands, and for how many ticks they have looked through a scope, if they do. */
    public record Posture(boolean moving, boolean aiming, boolean airborne, OptionalInt scopedTicks) {

        public static Posture of(Player shooter, boolean aiming) {
            var input = shooter.getCurrentInput();
            // Player deprecates isOnGround as client-reported; through Entity it is the same call without the warning.
            Entity body = shooter;
            var scoped = HotbarItem.of(shooter.getActiveItem(), Blaster.class).filter(Blaster::isLongRifle).isPresent()
                    ? OptionalInt.of(shooter.getActiveItemUsedTime()) : OptionalInt.empty();
            return new Posture(input.isForward() || input.isBackward() || input.isLeft() || input.isRight(), aiming,
                    !body.isOnGround() && !shooter.isInsideVehicle(), scoped);
        }

        public static Posture walking(boolean moving) {
            return new Posture(moving, false, false, OptionalInt.empty());
        }
    }

    public static WeaponHandling from(ConfigurationSection handling) {
        var defaults = Factors.from(handling, new Factors(1, 1, 1, 0, 1, 0));
        var overrides = handling.getConfigurationSection("blasters");
        var blasters = new EnumMap<Blaster, Factors>(Blaster.class);
        for (var blaster : Blaster.values()) {
            var own = overrides == null ? null : overrides.getConfigurationSection(blaster.key());
            var section = own != null || overrides == null ? own : overrides.getConfigurationSection(blaster.profile().key());
            blasters.put(blaster, section == null ? defaults : Factors.from(section, defaults));
        }
        return new WeaponHandling(Map.copyOf(blasters), Math.clamp(handling.getDouble("falloff-from"), 0, 1),
                Math.clamp(handling.getDouble("falloff-minimum"), 0, 1));
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
        return tick - sprintedAt <= blasters.get(blaster).sprintRecoveryTicks();
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
