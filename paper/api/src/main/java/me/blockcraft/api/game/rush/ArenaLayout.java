package me.blockcraft.api.game.rush;

import me.blockcraft.api.game.rush.RushConfig.Dimensions;
import me.blockcraft.api.game.rush.MapPool.Palette;
import me.blockcraft.api.game.rush.MapPool.Geometry;
import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public final class ArenaLayout {

    private final Dimensions dimensions;
    private final Palette palette;
    private final Geometry geometry;
    private final Map<Position, Material> blocks;

    public ArenaLayout(Dimensions dimensions, Palette palette, Geometry geometry) {
        this.dimensions = dimensions;
        this.palette = palette;
        this.geometry = geometry;
        blocks = createBlocks();
    }

    public ArenaLayout(Dimensions dimensions, Palette palette) {
        this(dimensions, palette, new Geometry(dimensions.baseRadius() * 2 + 1, 0, 0, 0));
    }

    public record Position(int x, int y, int z) { }

    public Dimensions dimensions() {
        return dimensions;
    }

    public Palette palette() {
        return palette;
    }

    public Geometry geometry() {
        return geometry;
    }

    public int baseX(int slot) {
        return slot == 0 ? -dimensions.baseRadius() - 1 : dimensions.bridgeLength() + dimensions.baseRadius();
    }

    public int spawnX(int slot) {
        return baseX(slot) + (slot == 0 ? 1 : -1);
    }

    public float spawnYaw(int slot) {
        return slot == 0 ? -90 : 90;
    }

    public Position bed(int slot, boolean head) {
        return new Position(baseX(slot) + (slot == 0 ? -1 : 1) * (head ? 2 : 1), dimensions.floorY() + 1, 0);
    }

    public int bedOwner(Position position) {
        for (int slot = 0; slot < 2; slot++) {
            if (bed(slot, false).equals(position) || bed(slot, true).equals(position)) {
                return slot;
            }
        }
        return -1;
    }

    public int minX() {
        return baseX(0) - dimensions.baseRadius() - dimensions.buildMargin();
    }

    public int maxX() {
        return baseX(1) + dimensions.baseRadius() + dimensions.buildMargin();
    }

    public int radiusZ() {
        return dimensions.baseRadius() + dimensions.buildMargin();
    }

    public boolean contains(Position position) {
        return position.x() >= minX() && position.x() <= maxX() && Math.abs(position.z()) <= radiusZ()
                && position.y() >= dimensions.floorY() && position.y() <= dimensions.floorY() + dimensions.buildHeight();
    }

    public Map<Position, Material> blocks() {
        return blocks;
    }

    private Map<Position, Material> createBlocks() {
        var blocks = new LinkedHashMap<Position, Material>();
        for (int slot = 0; slot < 2; slot++) {
            for (int x = baseX(slot) - dimensions.baseRadius(); x <= baseX(slot) + dimensions.baseRadius(); x++) {
                for (int z = -geometry.baseWidth() / 2; z <= geometry.baseWidth() / 2; z++) {
                    blocks.put(new Position(x, dimensions.floorY(), z), slot == 0 ? palette.redBase() : palette.blueBase());
                }
            }
            blocks.put(bed(slot, false), slot == 0 ? Material.RED_BED : Material.BLUE_BED);
            blocks.put(bed(slot, true), slot == 0 ? Material.RED_BED : Material.BLUE_BED);
        }
        for (int x = 0; x < dimensions.bridgeLength(); x++) {
            if (!geometry.bridgeAt(x, dimensions.bridgeLength())) {
                continue;
            }
            for (int z = -dimensions.bridgeWidth() / 2; z <= dimensions.bridgeWidth() / 2; z++) {
                blocks.put(new Position(x, dimensions.floorY(), z), palette.bridge());
            }
        }
        return Map.copyOf(blocks);
    }

    public void clear(Set<Position> placed, BiConsumer<Position, Material> write) {
        placed.forEach(position -> write.accept(position, Material.AIR));
        placed.clear();
        blocks().keySet().forEach(position -> write.accept(position, Material.AIR));
    }

    public void reset(Set<Position> placed, BiConsumer<Position, Material> write) {
        placed.forEach(position -> write.accept(position, Material.AIR));
        placed.clear();
        blocks().forEach(write);
    }

    public boolean canBuild(Position position) {
        if (!contains(position) || bedOwner(position) >= 0) {
            return false;
        }
        for (int slot = 0; slot < 2; slot++) {
            if (position.x() == spawnX(slot) && position.z() == 0
                    && position.y() <= dimensions.floorY() + 2) {
                return false;
            }
        }
        return true;
    }
}
