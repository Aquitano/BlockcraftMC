package me.blockcraft.story.cutscene;

import me.blockcraft.api.world.BlocksChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Real damage for a cutscene's explosions: each blast tears out the blocks around it, throws some of them as debris
 * that tumbles and comes to rest, and leaves fire and smoke behind. Every block it sets is remembered and announced
 * with a {@link BlocksChangedEvent}, so replays see the damage and {@link #restore} puts the building back.
 */
final class Demolition implements AutoCloseable {

    private static final Set<Material> STURDY = Set.of(Material.BEDROCK, Material.BARRIER, Material.LIGHT, Material.STRUCTURE_VOID);
    private static final int DEBRIS_PER_BLAST = 22;
    private static final int FLIGHT_TICKS = 70;
    private static final int STEP = 2;
    private static final double GRAVITY = 0.08;
    private static final double DRAG = 0.98;
    private static final double FIRE_CHANCE = 0.3;

    private final Plugin plugin;
    private final Map<Block, BlockData> originals = new LinkedHashMap<>();
    private final List<Debris> debris = new ArrayList<>();
    private final List<Location> craters = new ArrayList<>();
    private BukkitTask task;
    private int age;

    private static final class Debris {
        final BlockDisplay display;
        final Vector velocity;
        final Vector spin;
        final float scale;
        Location at;
        int flown;
        boolean resting;

        Debris(BlockDisplay display, Location at, Vector velocity, Vector spin, float scale) {
            this.display = display;
            this.at = at;
            this.velocity = velocity;
            this.spin = spin;
            this.scale = scale;
        }
    }

    Demolition(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Tears out a rough ball of {@code radius} around {@code center}, with its edge broken up so it reads as a blast. */
    void blast(Location center, double radius) {
        var random = ThreadLocalRandom.current();
        var world = center.getWorld();
        var torn = new ArrayList<Block>();
        int reach = (int) Math.ceil(radius * 1.15);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double distance = Math.sqrt(dx * dx + dy * dy * 1.4 + dz * dz);
                    if (distance > radius * (0.75 + random.nextDouble() * 0.4)) continue;
                    var block = world.getBlockAt(center.getBlockX() + dx, center.getBlockY() + dy, center.getBlockZ() + dz);
                    if (block.getType().isAir() || STURDY.contains(block.getType())) continue;
                    torn.add(block);
                }
            }
        }
        var shuffled = new ArrayList<>(torn);
        Collections.shuffle(shuffled, random);
        shuffled.stream().limit(DEBRIS_PER_BLAST).forEach(block -> throwDebris(block, center));
        var changed = new ArrayList<Block>(torn.size());
        for (var block : torn) {
            set(block, Material.AIR.createBlockData());
            changed.add(block);
        }
        for (var block : torn) {
            if (block.getRelative(BlockFace.DOWN).getType().isSolid() && random.nextDouble() < FIRE_CHANCE) {
                set(block, Material.FIRE.createBlockData());
            }
        }
        if (!changed.isEmpty()) Bukkit.getPluginManager().callEvent(new BlocksChangedEvent(changed));
        craters.add(center.clone());
        world.spawnParticle(Particle.FLASH, center, 2, 0, 0, 0, 0, Color.fromRGB(0xFFD9A0), true);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 3, radius * 0.4, radius * 0.3, radius * 0.4, 0, null, true);
        world.spawnParticle(Particle.FLAME, center, 120, radius * 0.5, radius * 0.4, radius * 0.5, 0.25, null, true);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 80, radius * 0.6, radius * 0.5, radius * 0.6, 0.12, null, true);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void set(Block block, BlockData data) {
        originals.putIfAbsent(block, block.getBlockData());
        block.setBlockData(data, false);
    }

    private void throwDebris(Block block, Location center) {
        var random = ThreadLocalRandom.current();
        var at = block.getLocation().add(0.5, 0.5, 0.5);
        var outward = at.toVector().subtract(center.toVector());
        if (outward.lengthSquared() < 0.01) outward = new Vector(random.nextDouble() - 0.5, 0.5, random.nextDouble() - 0.5);
        var velocity = outward.normalize().multiply(0.35 + random.nextDouble() * 0.6).add(new Vector(0, 0.45 + random.nextDouble() * 0.6, 0));
        var spin = new Vector(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).multiply(0.25);
        float scale = (float) (0.35 + random.nextDouble() * 0.45);
        var data = block.getBlockData();
        var display = at.getWorld().spawn(at, BlockDisplay.class, piece -> {
            piece.setBlock(data);
            piece.setPersistent(false);
            piece.setTeleportDuration(STEP);
            piece.setInterpolationDuration(STEP);
            piece.setBrightness(new Display.Brightness(12, 15));
            piece.setTransformation(transform(new Quaternionf(), scale));
        });
        debris.add(new Debris(display, at, velocity, spin, scale));
    }

    /** A cube of {@code scale} turned about its own middle, since a block display's origin is its corner. */
    private static Transformation transform(Quaternionf rotation, float scale) {
        var half = new Vector3f(-scale / 2).rotate(rotation);
        return new Transformation(half, rotation, new Vector3f(scale), new Quaternionf());
    }

    private void tick() {
        age++;
        for (var piece : debris) {
            if (piece.resting || !piece.display.isValid()) continue;
            piece.flown++;
            piece.velocity.setY(piece.velocity.getY() - GRAVITY).multiply(DRAG);
            var next = piece.at.clone().add(piece.velocity);
            if (next.getBlock().getType().isSolid() || piece.flown >= FLIGHT_TICKS) {
                piece.resting = true;
                next = piece.at;
            }
            piece.at = next;
            if (piece.flown % STEP != 0 && !piece.resting) continue;
            piece.display.teleport(piece.at);
            var turned = new Quaternionf().rotationXYZ((float) (piece.spin.getX() * piece.flown),
                    (float) (piece.spin.getY() * piece.flown), (float) (piece.spin.getZ() * piece.flown));
            piece.display.setInterpolationDelay(0);
            piece.display.setTransformation(transform(turned, piece.scale));
            if (piece.flown % 4 == 0) {
                piece.at.getWorld().spawnParticle(Particle.SMOKE, piece.at, 1, 0, 0, 0, 0.01, null, true);
            }
        }
        if (age % 3 != 0) return;
        for (var crater : craters) {
            var world = crater.getWorld();
            world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, crater, 2, 1.2, 0.4, 1.2, 0.02, null, true);
            world.spawnParticle(Particle.LARGE_SMOKE, crater, 3, 1.5, 0.6, 1.5, 0.03, null, true);
            world.spawnParticle(Particle.FLAME, crater, 4, 1.4, 0.5, 1.4, 0.01, null, true);
        }
    }

    /** Puts every block back as it was, removes the debris and puts out the smoke. */
    void restore() {
        if (task != null) task.cancel();
        task = null;
        debris.forEach(piece -> piece.display.remove());
        debris.clear();
        craters.clear();
        if (originals.isEmpty()) return;
        originals.forEach((block, data) -> block.setBlockData(data, false));
        Bukkit.getPluginManager().callEvent(new BlocksChangedEvent(originals.keySet()));
        originals.clear();
    }

    @Override
    public void close() {
        restore();
    }
}
