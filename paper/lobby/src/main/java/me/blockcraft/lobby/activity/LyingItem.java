package me.blockcraft.lobby.activity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * An item lying flat on top of a block, turned the way the spot faces, with a click box over it: a book on a table,
 * a note on a crate. Neither is saved into the map.
 */
record LyingItem(ItemDisplay display, Interaction box) {

    private static final float SCALE = 0.55f;
    private static final double ABOVE_BLOCK = 1.02;
    private static final float BOX_WIDTH = 0.6f;
    private static final float BOX_HEIGHT = 0.3f;

    /** {@code spot} is the block it lies on, as {@code /lobbyset} stores a looked-at block, with the setter's facing. */
    static LyingItem place(Location spot, ItemStack item) {
        var top = spot.toBlockLocation().add(0.5, ABOVE_BLOCK, 0.5);
        float yaw = (float) Math.toRadians(-spot.getYaw());
        var display = top.getWorld().spawn(top, ItemDisplay.class, spawned -> {
            spawned.setItemStack(item);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            spawned.setTransformation(new Transformation(new Vector3f(),
                    new Quaternionf().rotateY(yaw).rotateX((float) (Math.PI / 2)), new Vector3f(SCALE), new Quaternionf()));
            spawned.setPersistent(false);
        });
        var box = top.getWorld().spawn(top.clone().subtract(0, 0.02, 0), Interaction.class, spawned -> {
            spawned.setInteractionWidth(BOX_WIDTH);
            spawned.setInteractionHeight(BOX_HEIGHT);
            spawned.setResponsive(true);
            spawned.setPersistent(false);
        });
        return new LyingItem(display, box);
    }

    boolean owns(Entity entity) {
        return box.equals(entity) || display.equals(entity);
    }

    void remove() {
        display.remove();
        box.remove();
    }
}
