package me.blockcraft.lobby.gadget;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Optional;

/**
 * Lobby toys held in the gadget hotbar slot; the material doubles as the identifier. Where the pack has a model for a
 * gadget, the item asks for it and stays the same material, so it is still recognised as that gadget.
 */
public enum Gadget {
    GRAPPLING_HOOK("grappling-hook", Material.FISHING_ROD, Optional.of(PackModel.GADGET_GRAPPLING_HOOK)),
    TNT_JUMP("tnt-jump", Material.TNT, Optional.empty()),
    PAINT_GUN("paint-gun", Material.SNOWBALL, Optional.of(PackModel.GADGET_PAINT_GUN)),
    BIRTHDAY_CAKE("birthday-cake", Material.CAKE, Optional.empty());

    private final String key;
    private final Material material;
    private final Optional<PackModel> model;

    Gadget(String key, Material material, Optional<PackModel> model) {
        this.key = key;
        this.material = material;
        this.model = model;
    }

    public static Optional<Gadget> of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(gadget -> gadget.material == stack.getType()).findFirst();
    }

    public static Optional<Gadget> byKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }

    public String key() {
        return key;
    }

    public Material material() {
        return material;
    }

    /** The gadget's item, drawn with the pack's model where it has one. */
    public Items.Builder item() {
        var item = Items.of(material);
        model.ifPresent(found -> found.mark(item));
        return item;
    }
}
