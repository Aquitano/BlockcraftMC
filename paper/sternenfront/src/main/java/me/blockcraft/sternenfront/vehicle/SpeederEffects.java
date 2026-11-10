package me.blockcraft.sternenfront.vehicle;

import me.blockcraft.sternenfront.game.Faction;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.util.Vector;

final class SpeederEffects {

    private static final double ENGINE_HEIGHT = 0.4;
    private static final double GROUND_BELOW = 0.2;
    private static final double SAND_FROM_PACE = 0.1;

    private SpeederEffects() { }

    /** {@code pace} is the share of top speed. Only the sand is forced: it is what reads from across the basin. */
    static void wake(Location at, Vector forward, double tail, Faction faction, double pace, boolean boosting) {
        var world = at.getWorld();
        var rear = at.clone().add(forward.clone().multiply(-tail));
        var engine = rear.clone().add(0, ENGINE_HEIGHT, 0);
        world.spawnParticle(Particle.DUST, engine, 1 + (int) Math.round(pace * 2), 0.12, 0.08, 0.12, 0,
                new Particle.DustOptions(faction.bolt(), 1f));
        if (boosting) {
            world.spawnParticle(Particle.FLAME, engine, 2, 0.05, 0.05, 0.05, 0.01);
        }
        var ground = at.clone().subtract(0, GROUND_BELOW, 0).getBlock();
        if (pace >= SAND_FROM_PACE && ground.getType().isSolid()) {
            world.spawnParticle(Particle.BLOCK, rear, 1 + (int) Math.round(pace * 4), 0.4, 0.05, 0.4, 0.1,
                    ground.getBlockData(), true);
        }
    }

    static void damaged(Location at) {
        var hull = at.clone().add(0, 0.6, 0);
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, hull, 1, 0.2, 0.1, 0.2, 0.01);
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, hull, 2, 0.3, 0.15, 0.3, 0.05);
    }

    static void flash(Location muzzle, Faction faction) {
        muzzle.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, muzzle, 2, 0.05, 0.05, 0.05, 0.02);
        muzzle.getWorld().spawnParticle(Particle.DUST, muzzle, 3, 0.08, 0.08, 0.08, 0,
                new Particle.DustOptions(faction.bolt(), 1.2f));
    }

    static void rammed(Location at) {
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at, 10, 0.3, 0.4, 0.3, 0.15);
    }
}
