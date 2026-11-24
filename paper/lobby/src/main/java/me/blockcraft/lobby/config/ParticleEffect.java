package me.blockcraft.lobby.config;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** {@code count} of a particle, scattered {@code spread} blocks round a point and {@code verticalSpread} up and down. */
public record ParticleEffect(Particle particle, int count, double spread, double verticalSpread, double speed) {

    /** A section's {@code particle}, {@code count}, {@code spread} (half of it up and down) and {@code speed}. */
    public static Optional<ParticleEffect> fromYaml(Map<?, ?> section) {
        return named(section.get("particle")).map(particle -> {
            double spread = Math.clamp(number(section, "spread", 0.5), 0, 16);
            return new ParticleEffect(particle, (int) Math.clamp(number(section, "count", 1), 0, 50), spread, spread / 2,
                    Math.clamp(number(section, "speed", 0), 0, 2));
        });
    }

    /** Only particles without extra data, so a name in config.yml is all it takes. */
    public static Optional<Particle> named(Object name) {
        try {
            var particle = Particle.valueOf(String.valueOf(name).toUpperCase(Locale.ROOT));
            return particle.getDataType() == Void.class ? Optional.of(particle) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static double number(Map<?, ?> section, String key, double fallback) {
        return section.get(key) instanceof Number number ? number.doubleValue() : fallback;
    }

    public void show(Player viewer, Location at) {
        viewer.spawnParticle(particle, at, count, spread, verticalSpread, spread, speed);
    }
}
