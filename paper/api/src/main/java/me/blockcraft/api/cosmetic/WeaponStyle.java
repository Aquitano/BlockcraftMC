package me.blockcraft.api.cosmetic;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.blockcraft.api.pack.PackModel;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum WeaponStyle {
    STICK(null),
    BONE(PackModel.STICK_BONE),
    BLAZE_ROD(PackModel.STICK_BLAZE_ROD),
    FEATHER(PackModel.STICK_FEATHER),
    SWISS_FLAG(PackModel.STICK_SWISS_FLAG),
    GERMAN_FLAG(PackModel.STICK_GERMAN_FLAG),
    AUSTRIAN_FLAG(PackModel.STICK_AUSTRIAN_FLAG);

    private final PackModel model;

    WeaponStyle(PackModel model) {
        this.model = model;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public ItemStack icon() {
        return skin(ItemStack.of(Material.STICK));
    }

    public ItemStack skin(ItemStack stick) {
        var styled = stick.clone();
        if (styled.getType() != Material.STICK) {
            return styled;
        }
        if (model == null) {
            styled.unsetData(DataComponentTypes.CUSTOM_MODEL_DATA);
        } else {
            model.mark(styled);
        }
        return styled;
    }

    public static Optional<WeaponStyle> byKey(String key) {
        return Arrays.stream(values()).filter(style -> style.key().equals(key)).findFirst();
    }
}
