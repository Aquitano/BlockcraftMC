package me.blockcraft.bedwars.game;

import org.bukkit.Location;
import org.bukkit.Particle;

/** The flat galaxy of particles the original drew for a resource drop and a broken bed. */
final class Spiral {

    private static final int STRANDS = 8;
    private static final double CURVE = 10.0;
    private static final double ROTATION = Math.PI / 4;

    private Spiral() {
    }

    static <T> void draw(Location center, Particle particle, T data, int perStrand, double radius) {
        var world = center.getWorld();
        for (int strand = 1; strand <= STRANDS; strand++) {
            for (int step = 1; step <= perStrand; step++) {
                double ratio = (double) step / perStrand;
                double angle = CURVE * ratio * 2 * Math.PI / STRANDS + 2 * Math.PI * strand / STRANDS + ROTATION;
                var point = center.clone().add(Math.cos(angle) * ratio * radius, 0, Math.sin(angle) * ratio * radius);
                world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
            }
        }
    }
}
