package me.blockcraft.ttt.shop;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Goods that lie in the world once set down: the item itself on a display in the middle of the block space above the
 * floor, turned the way its owner looked, and a hitbox where it answers a click. The pack's model rests the item on
 * the floor; without the pack the display shows the plain item. All of it is gone at the round's end.
 */
final class Gadgets {

    private final Set<Entity> spawned = new HashSet<>();

    ItemDisplay show(Block space, float yaw, ItemStack look) {
        var display = space.getWorld().spawn(center(space, yaw), ItemDisplay.class, item -> {
            item.setItemStack(look);
            item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            item.setPersistent(false);
        });
        spawned.add(display);
        return display;
    }

    Interaction hitbox(Block space, float width, float height) {
        var hitbox = space.getWorld().spawn(space.getLocation().add(0.5, 0, 0.5), Interaction.class, interaction -> {
            interaction.setInteractionWidth(width);
            interaction.setInteractionHeight(height);
            interaction.setResponsive(true);
            interaction.setPersistent(false);
        });
        spawned.add(hitbox);
        return hitbox;
    }

    TextDisplay label(Block space, double height, Component text) {
        var label = space.getWorld().spawn(space.getLocation().add(0.5, height, 0.5), TextDisplay.class, display -> {
            display.text(text);
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
        });
        spawned.add(label);
        return label;
    }

    void remove(Entity... entities) {
        for (var entity : entities) {
            spawned.remove(entity);
            entity.remove();
        }
    }

    void clear() {
        spawned.forEach(Entity::remove);
        spawned.clear();
    }

    /** Whether a gadget can be set into {@code space}: free, with something solid to lie on. */
    static boolean fits(Block space) {
        return space.isPassable() && !space.isLiquid() && space.getRelative(0, -1, 0).isSolid();
    }

    private static Location center(Block space, float yaw) {
        var center = space.getLocation().add(0.5, 0.5, 0.5);
        center.setYaw(yaw);
        return center;
    }
}
