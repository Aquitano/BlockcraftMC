package me.blockcraft.sternenfront.appearance;

import me.blockcraft.api.item.Items;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/** What the round's display entities share: full light, the pack's paper models and the item display they spawn as. */
public final class Displays {

    public static final Display.Brightness LIT = new Display.Brightness(15, 15);

    private Displays() {
    }

    public static ItemStack model(String path) {
        return Items.of(Material.PAPER).model(Key.key("blockcraft", path)).build();
    }

    /** An item display that does not survive a restart; {@code setup} runs before it joins the world. */
    public static ItemDisplay item(Location at, ItemStack look, Consumer<ItemDisplay> setup) {
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setItemStack(look);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            setup.accept(display);
        });
    }
}
