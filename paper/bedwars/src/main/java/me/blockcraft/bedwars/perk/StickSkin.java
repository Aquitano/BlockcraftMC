package me.blockcraft.bedwars.perk;

import me.blockcraft.api.cosmetic.WeaponStyle;
import me.blockcraft.api.cosmetic.WeaponStyles;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Optional;

/** What the knockback stick from the shop looks like in its owner's hands; it hits the same whatever it is. */
public enum StickSkin implements Perk {
    STICK(WeaponStyle.STICK),
    BONE(WeaponStyle.BONE),
    BLAZE_ROD(WeaponStyle.BLAZE_ROD),
    FEATHER(WeaponStyle.FEATHER),
    SWISS_FLAG(WeaponStyle.SWISS_FLAG),
    GERMAN_FLAG(WeaponStyle.GERMAN_FLAG),
    AUSTRIAN_FLAG(WeaponStyle.AUSTRIAN_FLAG);

    private final WeaponStyle style;

    StickSkin(WeaponStyle style) {
        this.style = style;
    }

    @Override
    public String key() {
        return style.key();
    }

    @Override
    public ItemStack icon() {
        return style.icon();
    }

    /** {@code stick} as this skin, its name, enchantments and amount kept. */
    public ItemStack skin(ItemStack stick) {
        return style.skin(stick);
    }

    /** Marks a stick from the shop, so that it changes skin when it changes hands and nothing else does. */
    public static ItemStack tag(ItemStack stick) {
        return WeaponStyles.tag(stick);
    }

    public static boolean isStick(ItemStack item) {
        return WeaponStyles.isStick(item);
    }

    public static Optional<StickSkin> byKey(String key) {
        return Arrays.stream(values()).filter(skin -> skin.key().equals(key)).findFirst();
    }
}
