package me.blockcraft.api.pack;

import me.blockcraft.api.item.Items;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Optional;

/**
 * The pack's 3D models for things other players see: an item held or worn, or shown on a display. Such an item stays
 * its vanilla {@code fallback} and only asks for the model through custom_model_data; the pack's
 * {@code assets/minecraft/items/<fallback>.json} answers with {@code models/item/<path>.json}, and a client without
 * the pack draws the vanilla item. {@code PackOverridesTest} fails when the pack lacks a model or its case.
 */
public enum PackModel {

    ZOMBIES_PISTOL(Material.WOODEN_HOE, "zombies/pistol"),
    ZOMBIES_RIFLE(Material.IRON_HOE, "zombies/rifle"),
    ZOMBIES_SHOTGUN(Material.STONE_HOE, "zombies/shotgun"),
    ZOMBIES_SNIPER(Material.DIAMOND_HOE, "zombies/sniper"),
    ZOMBIES_FLAMETHROWER(Material.GOLDEN_HOE, "zombies/flamethrower"),
    TTT_MEDIPACK(Material.GLISTERING_MELON_SLICE, "ttt/medipack"),
    TTT_RADAR(Material.COMPASS, "ttt/radar"),
    TTT_DNA_SCANNER(Material.BLAZE_ROD, "ttt/dna_scanner"),
    TTT_GOLDEN_SWORD(Material.GOLDEN_SWORD, "ttt/golden_sword"),
    ENDERGAMES_TRACKER(Material.COMPASS, "endergames/tracker"),
    SKYWARS_HAMMER(Material.STONE_PICKAXE, "skywars/hammer"),
    HAT_CROWN(Material.GOLDEN_HELMET, "hats/crown"),
    HAT_TOP_HAT(Material.LEATHER_HELMET, "hats/top_hat"),
    HAT_WITCH_HAT(Material.LEATHER_HELMET, "hats/witch_hat"),
    GADGET_GRAPPLING_HOOK(Material.FISHING_ROD, "gadgets/grappling_hook"),
    GADGET_PAINT_GUN(Material.SNOWBALL, "gadgets/paint_gun"),
    PROP_STAR(Material.NETHER_STAR, "props/star"),
    PROP_KNIFE(Material.IRON_SWORD, "props/knife");

    private final Material fallback;
    private final String path;

    PackModel(Material fallback, String path) {
        this.fallback = fallback;
        this.path = path;
    }

    public Material fallback() {
        return fallback;
    }

    /** What the item carries as custom_model_data, and the case the pack selects on. */
    public String key() {
        return "blockcraft:" + path;
    }

    public Key model() {
        return Key.key("blockcraft", "item/" + path);
    }

    /** How config files name the model, such as {@code props/star}. */
    public String path() {
        return path;
    }

    public static Optional<PackModel> byPath(String path) {
        return Arrays.stream(values()).filter(model -> model.path.equals(path)).findFirst();
    }

    public Items.Builder item() {
        return mark(Items.of(fallback));
    }

    /** Asks for this model on an item built elsewhere, which must be of the {@code fallback} material. */
    public Items.Builder mark(Items.Builder item) {
        return item.modelData(key());
    }

    public ItemStack mark(ItemStack item) {
        return mark(Items.of(item)).build();
    }
}
