package me.blockcraft.lobby.cosmetic;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.blockcraft.api.pack.PackModel;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Optional;

/**
 * The 3D hats, worn in the head slot like a {@link Head}. Each is a helmet that asks for the pack's model; a player
 * without the pack sees the helmet, coloured to match where it is leather.
 */
public enum Hat {
    CROWN("crown", PackModel.HAT_CROWN, Optional.empty()),
    TOP_HAT("top-hat", PackModel.HAT_TOP_HAT, Optional.of(Color.fromRGB(0x1D, 0x1D, 0x21))),
    WITCH_HAT("witch-hat", PackModel.HAT_WITCH_HAT, Optional.of(Color.fromRGB(0x89, 0x32, 0xB8)));

    public static final String ITEM_PREFIX = "lobby.hat.";

    private final String key;
    private final PackModel model;
    private final Optional<Color> color;

    Hat(String key, PackModel model, Optional<Color> color) {
        this.key = key;
        this.model = model;
        this.color = color;
    }

    public static Optional<Hat> byKey(String key) {
        return Arrays.stream(values()).filter(hat -> hat.key.equals(key)).findFirst();
    }

    public String key() {
        return key;
    }

    public String item() {
        return ITEM_PREFIX + key;
    }

    public ItemStack stack(Component name) {
        var builder = model.item().name(name).hideAttributes();
        color.ifPresent(builder::color);
        var stack = builder.build();
        // The client draws worn armour as armour and never as its item model; without equippable it draws the hat.
        stack.unsetData(DataComponentTypes.EQUIPPABLE);
        return stack;
    }
}
