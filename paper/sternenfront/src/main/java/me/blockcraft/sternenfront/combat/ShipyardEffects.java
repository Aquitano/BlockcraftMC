package me.blockcraft.sternenfront.combat;

import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class ShipyardEffects {

    private final SternenfrontGame game;
    private final boolean enabled;
    private final double rangeSquared;
    private final int steamInterval;
    private final int sparkInterval;
    private final int steamCount;
    private final int sparkCount;
    private final int cranePeriod;
    private final int craneStep;
    private final float cableLength;
    private final float volume;
    private final List<BlockDisplay> crane = new ArrayList<>();

    ShipyardEffects(SternenfrontGame game) {
        this.game = game;
        var config = game.plugin().getConfig();
        String path = "presentation.shipyard.";
        enabled = config.getBoolean("presentation.effects") && config.getBoolean(path + "enabled");
        rangeSquared = Math.pow(Math.clamp(config.getDouble(path + "range"), 1, 96), 2);
        steamInterval = Math.max(1, config.getInt(path + "steam-interval-ticks"));
        sparkInterval = Math.max(1, config.getInt(path + "spark-interval-ticks"));
        steamCount = Math.clamp(config.getInt(path + "steam-particles"), 0, 20);
        sparkCount = Math.clamp(config.getInt(path + "spark-particles"), 0, 20);
        cranePeriod = Math.max(80, config.getInt(path + "crane-period-ticks"));
        craneStep = Math.clamp(config.getInt(path + "crane-step-ticks"), 1, 20);
        cableLength = (float) Math.clamp(config.getDouble(path + "crane-cable-length"), 1, 8);
        volume = (float) Math.clamp(config.getDouble(path + "volume"), 0, 1);
    }

    void tick(long tick) {
        if (!enabled) return;
        var environment = game.map().environment();
        emit(environment.steam(), tick, steamInterval, Particle.WHITE_SMOKE, steamCount, Sound.BLOCK_FIRE_EXTINGUISH, 0.7f);
        emit(environment.sparks(), tick, sparkInterval, Particle.ELECTRIC_SPARK, sparkCount, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1.4f);
        if (tick % craneStep != 0) return;
        environment.crane().ifPresent(track -> {
            var at = track.from().clone().add(track.to().toVector().subtract(track.from().toVector()).multiply(travel(tick, cranePeriod)));
            if (!loaded(at) || at.getWorld().getPlayers().stream().noneMatch(player -> player.getLocation().distanceSquared(at) <= rangeSquared)) return;
            if (crane.isEmpty()) {
                part(at, Material.GRAY_CONCRETE, -1.1f, 0, -0.8f, 2.2f, 0.65f, 1.6f);
                part(at, Material.IRON_BLOCK, -0.7f, -0.25f, -0.6f, 1.4f, 0.3f, 1.2f);
                part(at, Material.IRON_CHAIN, -0.25f, -cableLength, -0.25f, 0.5f, cableLength, 0.5f);
                part(at, Material.DEEPSLATE_TILES, -0.4f, -cableLength - 0.5f, -0.4f, 0.8f, 0.5f, 0.8f);
                part(at, Material.WAXED_WEATHERED_COPPER, -1.2f, -cableLength - 1.6f, -0.6f, 2.4f, 1.1f, 1.2f);
                part(at, Material.IRON_BLOCK, -0.15f, -cableLength - 1.62f, -0.62f, 0.3f, 1.14f, 1.24f);
            }
            crane.forEach(display -> display.teleport(at));
        });
    }

    static double travel(long tick, int period) {
        return (1 - Math.cos(Math.floorMod(tick, period) * Math.PI * 2 / period)) / 2;
    }

    private void emit(List<Location> sites, long tick, int interval, Particle particle, int count, Sound sound, float pitch) {
        if (count == 0) return;
        for (int index = 0; index < sites.size(); index++) {
            long phase = tick + index * 7L;
            if (phase % interval != 0) continue;
            var at = sites.get(index);
            if (!loaded(at)) continue;
            for (var viewer : at.getWorld().getPlayers()) {
                if (viewer.getLocation().distanceSquared(at) > rangeSquared) continue;
                viewer.spawnParticle(particle, at, count, 0.18, 0.15, 0.18, 0.025);
                if (phase % (interval * 8L) == 0 && volume > 0) {
                    viewer.playSound(at, sound, SoundCategory.AMBIENT, volume, pitch);
                }
            }
        }
    }

    private static boolean loaded(Location at) {
        return at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }

    private void part(Location at, Material material, float x, float y, float z, float width, float height, float depth) {
        crane.add(at.getWorld().spawn(at, BlockDisplay.class, display -> {
            display.setPersistent(false);
            display.setBlock(material.createBlockData());
            display.setTeleportDuration(craneStep);
            display.setDisplayWidth(12);
            display.setDisplayHeight(20);
            display.setViewRange((float) (Math.sqrt(rangeSquared) / 64));
            display.setTransformation(new Transformation(new Vector3f(x, y, z), new Quaternionf(),
                    new Vector3f(width, height, depth), new Quaternionf()));
        }));
    }

    void clear() {
        crane.forEach(Entity::remove);
        crane.clear();
    }
}
