package me.blockcraft.lobby.gadget;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Optional;

/** Lobby toys held in the gadget hotbar slot; the material doubles as the identifier. */
public enum Gadget {
    GRAPPLING_HOOK("grappling-hook", Material.FISHING_ROD),
    TNT_JUMP("tnt-jump", Material.TNT),
    PAINT_GUN("paint-gun", Material.SNOWBALL);

    private final String key;
    private final Material material;

    Gadget(String key, Material material) {
        this.key = key;
        this.material = material;
    }

    public static Optional<Gadget> of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(gadget -> gadget.material == stack.getType()).findFirst();
    }

    public String key() {
        return key;
    }

    public Material material() {
        return material;
    }
}
