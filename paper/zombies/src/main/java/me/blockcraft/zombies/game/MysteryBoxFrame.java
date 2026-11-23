package me.blockcraft.zombies.game;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

final class MysteryBoxFrame {

    private final List<BlockDisplay> parts = new ArrayList<>();
    private final BlockDisplay lamp;

    MysteryBoxFrame(Block chest) {
        var centre = chest.getLocation().add(0.5, 0, 0.5);
        if (chest.getBlockData() instanceof Directional facing) {
            centre.setDirection(facing.getFacing().getDirection());
        }
        part(centre, Material.DARK_OAK_PLANKS, -8, 0, -8, 16, 2, 16);
        for (int x : new int[] {-8, 6}) {
            part(centre, Material.DARK_OAK_PLANKS, x, 2, -8, 2, 5, 16);
            for (int z : new int[] {-8, 6}) {
                part(centre, Material.CUT_COPPER, x, 7, z, 2, 1, 2);
            }
        }
        part(centre, Material.DARK_OAK_PLANKS, -6, 2, 7, 12, 4, 1);
        part(centre, Material.CUT_COPPER, -3, 2, 8, 6, 5, 1);
        lamp = part(centre, Material.REDSTONE_LAMP, -2, 3, 9, 4, 3, 1);
    }

    /** Local model pixels measured from the centre of the block's floor. Positive Z faces the buyer. */
    private BlockDisplay part(Location centre, Material material, int x, int y, int z, int width, int height, int depth) {
        var part = centre.getWorld().spawn(centre, BlockDisplay.class, display -> {
            display.setBlock(material.createBlockData());
            display.setTransformation(new Transformation(new Vector3f(x, y, z).div(16), new Quaternionf(),
                    new Vector3f(width, height, depth).div(16), new Quaternionf()));
            display.setPersistent(false);
        });
        parts.add(part);
        return part;
    }

    void lit(boolean lit) {
        var data = (Lightable) lamp.getBlock();
        data.setLit(lit);
        lamp.setBlock(data);
        lamp.setBrightness(lit ? new Display.Brightness(15, 15) : null);
    }

    void remove() {
        parts.forEach(BlockDisplay::remove);
    }
}
