package me.blockcraft.zombies.game;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.zombies.arena.ArenaMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** What the arena does by itself: doors roll up, power spreads from the generator, lights flicker, the horde groans. */
final class ArenaEffects {

    private static final int DOOR_ROW_TICKS = 6;
    private static final double POWER_TICKS_PER_BLOCK = 0.6;
    private static final int FLICKER_TICKS = 3;
    private final Plugin plugin;
    private final ArenaMap arena;
    private final World world;
    private boolean powered;
    private int nextFlicker;
    private int nextGroan;

    ArenaEffects(Plugin plugin, ArenaMap arena) {
        this.plugin = plugin;
        this.arena = arena;
        this.world = arena.world();
    }

    void openDoor(ArenaMap.Room room) {
        var rows = arena.open(room);
        var door = room.door();
        var center = new Location(world, (door.minX() + door.maxX() + 1) / 2.0, door.minY() + 1.5, (door.minZ() + door.maxZ() + 1) / 2.0);
        world.playSound(center, Sound.BLOCK_PISTON_CONTRACT, 1, 0.6f);
        for (int row = 0; row < rows.size(); row++) {
            var blocks = rows.get(row);
            float pitch = 0.7f + row * 0.15f;
            Tasks.later(plugin, (long) DOOR_ROW_TICKS * (row + 1), () -> {
                blocks.stream().filter(block -> !block.getType().isAir()).forEach(block -> {
                    world.spawnParticle(Particle.BLOCK, centerOf(block), 6, 0.3, 0.3, 0.3, block.getBlockData());
                    block.setType(Material.AIR, false);
                });
                world.playSound(center, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.8f, pitch);
            });
        }
    }

    void powerOn(Location source) {
        powered = true;
        world.playSound(source, Sound.BLOCK_BEACON_ACTIVATE, 2, 0.7f);
        for (var light : arena.lights()) {
            Tasks.later(plugin, 1 + Math.round(light.getLocation().distance(source) * POWER_TICKS_PER_BLOCK), () -> {
                ArenaMap.lit(light, true);
                world.playSound(centerOf(light), Sound.BLOCK_COPPER_BULB_TURN_ON, 0.6f, 1);
                world.spawnParticle(Particle.ELECTRIC_SPARK, centerOf(light), 6, 0.4, 0.4, 0.4);
            });
        }
    }

    void horn(boolean boss) {
        var windows = arena.activeWindows();
        if (!windows.isEmpty()) {
            world.playSound(pick(windows).spawn(), Sound.EVENT_RAID_HORN, 6, boss ? 0.7f : 1);
        }
    }

    void tick(int tick, boolean groans) {
        var random = ThreadLocalRandom.current();
        if (tick >= nextFlicker) {
            nextFlicker = tick + random.nextInt(60, 180);
            flicker();
        }
        if (groans && tick >= nextGroan && !arena.windows().isEmpty()) {
            nextGroan = tick + random.nextInt(80, 200);
            world.playSound(pick(arena.windows()).spawn(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.2f, random.nextFloat(0.5f, 0.8f));
        }
        if (tick % 10 == 0) {
            arena.activeWindows().forEach(window -> world.spawnParticle(Particle.SMOKE,
                    window.spawn().clone().add(0, 0.3, 0), 2, 1.2, 0.2, 1.2, 0.005));
        }
    }

    private void flicker() {
        var lights = arena.lights();
        if (lights.isEmpty()) {
            return;
        }
        var light = pick(lights);
        ArenaMap.lit(light, !powered);
        world.playSound(centerOf(light), Sound.BLOCK_COPPER_BULB_TURN_OFF, 0.3f, 1.4f);
        Tasks.later(plugin, FLICKER_TICKS + ThreadLocalRandom.current().nextInt(4), () -> ArenaMap.lit(light, powered));
    }

    private static <T> T pick(List<T> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private static Location centerOf(Block block) {
        return block.getLocation().add(0.5, 0.5, 0.5);
    }
}
