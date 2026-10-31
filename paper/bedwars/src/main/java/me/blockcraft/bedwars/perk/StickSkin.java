package me.blockcraft.bedwars.perk;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** What the knockback stick from the shop looks like in its owner's hands; it hits the same whatever it is. */
public enum StickSkin implements Perk {
    STICK(Material.STICK, List.of()),
    BONE(Material.BONE, List.of()),
    BLAZE_ROD(Material.BLAZE_ROD, List.of()),
    FEATHER(Material.FEATHER, List.of()),
    SWISS_FLAG(Material.RED_BANNER, List.of(
            new Pattern(DyeColor.WHITE, PatternType.STRIPE_MIDDLE),
            new Pattern(DyeColor.WHITE, PatternType.STRIPE_CENTER),
            new Pattern(DyeColor.RED, PatternType.BORDER),
            new Pattern(DyeColor.RED, PatternType.STRIPE_TOP),
            new Pattern(DyeColor.RED, PatternType.STRIPE_BOTTOM))),
    GERMAN_FLAG(Material.RED_BANNER, List.of(
            new Pattern(DyeColor.YELLOW, PatternType.STRIPE_LEFT),
            new Pattern(DyeColor.BLACK, PatternType.STRIPE_RIGHT))),
    AUSTRIAN_FLAG(Material.RED_BANNER, List.of(
            new Pattern(DyeColor.WHITE, PatternType.STRIPE_CENTER)));

    private static final NamespacedKey TAG = new NamespacedKey("blockcraft", "bedwars_stick");

    private final Material material;
    private final List<Pattern> patterns;

    StickSkin(Material material, List<Pattern> patterns) {
        this.material = material;
        this.patterns = patterns;
    }

    @Override
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Override
    public ItemStack icon() {
        return skin(ItemStack.of(material));
    }

    /** {@code stick} as this skin, its name, enchantments and amount kept. */
    public ItemStack skin(ItemStack stick) {
        var skinned = stick.withType(material);
        if (patterns.isEmpty()) {
            skinned.unsetData(DataComponentTypes.BANNER_PATTERNS);
        } else {
            skinned.editMeta(BannerMeta.class, meta -> meta.setPatterns(patterns));
            skinned.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                    TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.BANNER_PATTERNS).build());
        }
        return skinned;
    }

    /** Marks a stick from the shop, so that it changes skin when it changes hands and nothing else does. */
    public static ItemStack tag(ItemStack stick) {
        stick.editMeta(meta -> meta.getPersistentDataContainer().set(TAG, PersistentDataType.BOOLEAN, true));
        return stick;
    }

    public static boolean isStick(ItemStack item) {
        return item != null && !item.isEmpty() && item.getPersistentDataContainer().has(TAG);
    }

    public static Optional<StickSkin> byKey(String key) {
        return Arrays.stream(values()).filter(skin -> skin.key().equals(key)).findFirst();
    }
}
