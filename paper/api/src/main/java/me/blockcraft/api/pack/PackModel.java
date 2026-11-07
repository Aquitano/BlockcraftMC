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
    ZOMBIES_DYNAMITE(Material.TNT, "zombies/dynamite"),
    ZOMBIES_INFERNO(Material.CARVED_PUMPKIN, "zombies/inferno"),
    // A stone button worn on the head sits inside it, so a client without the pack shows the bare head.
    ZOMBIES_RIOT_HELMET(Material.STONE_BUTTON, "zombies/riot_helmet"),
    ZOMBIES_RUNNER_GOGGLES(Material.STONE_BUTTON, "zombies/runner_goggles"),
    ZOMBIES_GAS_MASK(Material.STONE_BUTTON, "zombies/gas_mask"),
    ZOMBIES_ARSONIST_MASK(Material.STONE_BUTTON, "zombies/arsonist_mask"),
    ZOMBIES_SHIELD_HELM(Material.STONE_BUTTON, "zombies/shield_helm"),
    ZOMBIES_BROOD_SACS(Material.STONE_BUTTON, "zombies/brood_sacs"),
    ZOMBIES_CRAWLER_EYES(Material.STONE_BUTTON, "zombies/crawler_eyes"),
    ZOMBIES_TESLA_HELMET(Material.STONE_BUTTON, "zombies/tesla_helmet"),
    ZOMBIES_SHADOW_HOOD(Material.STONE_BUTTON, "zombies/shadow_hood"),
    ZOMBIES_FROST_CROWN(Material.PACKED_ICE, "zombies/frost_crown"),
    ZOMBIES_MOLOTOV(Material.HONEY_BOTTLE, "zombies/molotov"),
    TTT_MEDIPACK(Material.GLISTERING_MELON_SLICE, "ttt/medipack"),
    STERNENFRONT_CARBINE(Material.IRON_HOE, "sternenfront/carbine", true),
    STERNENFRONT_REPEATER(Material.NETHERITE_HOE, "sternenfront/repeater", true),
    STERNENFRONT_LONG_RIFLE(Material.DIAMOND_HOE, "sternenfront/long_rifle", true),
    STERNENFRONT_PISTOL(Material.STONE_HOE, "sternenfront/pistol", true),
    STERNENFRONT_GRENADE(Material.FIREWORK_STAR, "sternenfront/grenade"),
    STERNENFRONT_ADRENALINE(Material.SUGAR, "sternenfront/adrenaline"),
    STERNENFRONT_SHIELD_EMITTER(Material.PRISMARINE_CRYSTALS, "sternenfront/shield_emitter"),
    STERNENFRONT_JUMP_PACK(Material.FEATHER, "sternenfront/jump_pack"),
    STERNENFRONT_MED_STATION(Material.GLISTERING_MELON_SLICE, "sternenfront/med_station"),
    STERNENFRONT_SIEGE_MODE(Material.NETHERITE_SCRAP, "sternenfront/siege_mode"),
    STERNENFRONT_MINING_CHARGE(Material.GUNPOWDER, "sternenfront/mining_charge"),
    STERNENFRONT_RELIC_BLADE(Material.NETHERITE_SWORD, "sternenfront/relic_blade"),
    STERNENFRONT_HELMET_KOLONIEN(Material.STONE_BUTTON, "sternenfront/helmet_kolonien"),
    STERNENFRONT_HELMET_HEGEMONIE(Material.STONE_BUTTON, "sternenfront/helmet_hegemonie"),
    // Bolts only fly on item displays; a client without the pack sees an end rod in the faction's glow.
    STERNENFRONT_BOLT_KOLONIEN(Material.END_ROD, "sternenfront/bolt_kolonien"),
    STERNENFRONT_BOLT_HEGEMONIE(Material.END_ROD, "sternenfront/bolt_hegemonie"),
    STERNENFRONT_LIGHTSABER_JEDI(Material.DIAMOND_SWORD, "sternenfront/lightsaber_jedi", true),
    STERNENFRONT_LIGHTSABER_JEDI_OFF(Material.DIAMOND_SWORD, "sternenfront/lightsaber_jedi_off"),
    STERNENFRONT_LIGHTSABER_SITH(Material.NETHERITE_SWORD, "sternenfront/lightsaber_sith", true),
    STERNENFRONT_LIGHTSABER_SITH_OFF(Material.NETHERITE_SWORD, "sternenfront/lightsaber_sith_off"),
    STERNENFRONT_HOOD_JEDI(Material.STONE_BUTTON, "sternenfront/hood_jedi"),
    STERNENFRONT_MASK_SITH(Material.STONE_BUTTON, "sternenfront/mask_sith"),
    STERNENFRONT_HELMET_BOUNTY_HUNTER(Material.STONE_BUTTON, "sternenfront/helmet_bounty_hunter"),
    STERNENFRONT_HELMET_ARC(Material.STONE_BUTTON, "sternenfront/helmet_arc"),
    STERNENFRONT_HELMET_COMMANDO(Material.STONE_BUTTON, "sternenfront/helmet_commando"),
    STERNENFRONT_CAP_COMMANDER(Material.STONE_BUTTON, "sternenfront/cap_commander"),
    STERNENFRONT_TWIN_PISTOL(Material.GOLDEN_HOE, "sternenfront/twin_pistol", true),
    STERNENFRONT_HEAVY_RIFLE(Material.WOODEN_HOE, "sternenfront/heavy_rifle", true),
    STERNENFRONT_CARBINE_FIRING(Material.IRON_HOE, "sternenfront/carbine_firing", true),
    STERNENFRONT_REPEATER_FIRING(Material.NETHERITE_HOE, "sternenfront/repeater_firing", true),
    STERNENFRONT_LONG_RIFLE_FIRING(Material.DIAMOND_HOE, "sternenfront/long_rifle_firing", true),
    STERNENFRONT_PISTOL_FIRING(Material.STONE_HOE, "sternenfront/pistol_firing", true),
    STERNENFRONT_TWIN_PISTOL_FIRING(Material.GOLDEN_HOE, "sternenfront/twin_pistol_firing", true),
    STERNENFRONT_HEAVY_RIFLE_FIRING(Material.WOODEN_HOE, "sternenfront/heavy_rifle_firing", true),
    STERNENFRONT_E5(Material.IRON_HOE, "sternenfront/e5", true),
    STERNENFRONT_E5_FIRING(Material.IRON_HOE, "sternenfront/e5_firing", true),
    STERNENFRONT_WRIST_BLASTER(Material.NETHERITE_HOE, "sternenfront/wrist_blaster", true),
    STERNENFRONT_WRIST_BLASTER_FIRING(Material.NETHERITE_HOE, "sternenfront/wrist_blaster_firing", true),
    STERNENFRONT_E5S(Material.DIAMOND_HOE, "sternenfront/e5s", true),
    STERNENFRONT_E5S_FIRING(Material.DIAMOND_HOE, "sternenfront/e5s_firing", true),
    STERNENFRONT_SE14(Material.STONE_HOE, "sternenfront/se14", true),
    STERNENFRONT_SE14_FIRING(Material.STONE_HOE, "sternenfront/se14_firing", true),
    STERNENFRONT_WESTAR_PISTOLS(Material.GOLDEN_HOE, "sternenfront/westar_pistols", true),
    STERNENFRONT_WESTAR_PISTOLS_FIRING(Material.GOLDEN_HOE, "sternenfront/westar_pistols_firing", true),
    STERNENFRONT_CARBINE_LOWERED(Material.IRON_HOE, "sternenfront/carbine_lowered", true),
    STERNENFRONT_REPEATER_LOWERED(Material.NETHERITE_HOE, "sternenfront/repeater_lowered", true),
    STERNENFRONT_LONG_RIFLE_LOWERED(Material.DIAMOND_HOE, "sternenfront/long_rifle_lowered", true),
    STERNENFRONT_PISTOL_LOWERED(Material.STONE_HOE, "sternenfront/pistol_lowered", true),
    STERNENFRONT_TWIN_PISTOL_LOWERED(Material.GOLDEN_HOE, "sternenfront/twin_pistol_lowered", true),
    STERNENFRONT_HEAVY_RIFLE_LOWERED(Material.WOODEN_HOE, "sternenfront/heavy_rifle_lowered", true),
    STERNENFRONT_E5_LOWERED(Material.IRON_HOE, "sternenfront/e5_lowered", true),
    STERNENFRONT_WRIST_BLASTER_LOWERED(Material.NETHERITE_HOE, "sternenfront/wrist_blaster_lowered", true),
    STERNENFRONT_E5S_LOWERED(Material.DIAMOND_HOE, "sternenfront/e5s_lowered", true),
    STERNENFRONT_SE14_LOWERED(Material.STONE_HOE, "sternenfront/se14_lowered", true),
    STERNENFRONT_WESTAR_PISTOLS_LOWERED(Material.GOLDEN_HOE, "sternenfront/westar_pistols_lowered", true),
    STERNENFRONT_ORBITAL_BEACON(Material.ECHO_SHARD, "sternenfront/orbital_beacon"),
    STERNENFRONT_KYBER_CRYSTAL(Material.AMETHYST_SHARD, "sternenfront/kyber_crystal"),
    STERNENFRONT_BRUMMI(Material.OBSERVER, "sternenfront/brummi"),
    STERNENFRONT_SAND_BEAST(Material.SKELETON_SKULL, "sternenfront/sand_beast"),
    STERNENFRONT_YARD_CONSOLE(Material.COMPARATOR, "sternenfront/yard/console"),
    STERNENFRONT_YARD_POWER_COUPLER(Material.LIGHTNING_ROD, "sternenfront/yard/power_coupler"),
    STERNENFRONT_YARD_ENGINE_CRADLE(Material.OBSERVER, "sternenfront/yard/engine_cradle"),
    STERNENFRONT_YARD_CARGO_STACK(Material.CHEST, "sternenfront/yard/cargo_stack"),
    STERNENFRONT_YARD_REPAIR_DROID(Material.OBSERVER, "sternenfront/yard/repair_droid"),
    STERNENFRONT_YARD_WALL_PANEL(Material.IRON_TRAPDOOR, "sternenfront/yard/wall_panel"),
    STERNENFRONT_YARD_CLONE_TANK(Material.OBSERVER, "sternenfront/yard/clone_tank"),
    STERNENFRONT_YARD_EMBRYO_RACK(Material.OBSERVER, "sternenfront/yard/embryo_rack"),
    STERNENFRONT_YARD_GENE_ARCHIVE(Material.OBSERVER, "sternenfront/yard/gene_archive"),
    TTT_RADAR(Material.COMPASS, "ttt/radar"),
    TTT_DNA_SCANNER(Material.BLAZE_ROD, "ttt/dna_scanner"),
    TTT_GOLDEN_SWORD(Material.GOLDEN_SWORD, "ttt/golden_sword"),
    TTT_KNIFE(Material.SHEARS, "ttt/knife"),
    TTT_MASK(Material.LEATHER, "ttt/mask"),
    TTT_LIGHTER(Material.FLINT_AND_STEEL, "ttt/lighter"),
    TTT_TIME_BOMB(Material.TNT, "ttt/time_bomb"),
    TTT_SILENCED_PISTOL(Material.IRON_HOE, "ttt/silenced_pistol"),
    TTT_BEAR_TRAP(Material.IRON_TRAPDOOR, "ttt/bear_trap"),
    TTT_POISON(Material.FERMENTED_SPIDER_EYE, "ttt/poison"),
    TTT_JAMMER(Material.LIGHTNING_ROD, "ttt/jammer"),
    TTT_MOTION_SENSOR(Material.SCULK_SENSOR, "ttt/motion_sensor"),
    TTT_TASER(Material.BREEZE_ROD, "ttt/taser"),
    TTT_DEFIBRILLATOR(Material.HEART_OF_THE_SEA, "ttt/defibrillator"),
    TTT_MINE_DETECTOR(Material.ECHO_SHARD, "ttt/mine_detector"),
    TTT_CRIME_CAMERA(Material.ENDER_EYE, "ttt/crime_camera"),

    STICK_BONE(Material.STICK, "weapons/bone"),
    STICK_BLAZE_ROD(Material.STICK, "weapons/blaze_rod"),
    STICK_FEATHER(Material.STICK, "weapons/feather"),
    STICK_SWISS_FLAG(Material.STICK, "weapons/swiss_flag"),
    STICK_GERMAN_FLAG(Material.STICK, "weapons/german_flag"),
    STICK_AUSTRIAN_FLAG(Material.STICK, "weapons/austrian_flag"),
    ENDERGAMES_TRACKER(Material.COMPASS, "endergames/tracker"),
    SKYWARS_HAMMER(Material.STONE_PICKAXE, "skywars/hammer"),
    HAT_CROWN(Material.GOLDEN_HELMET, "hats/crown"),
    HAT_TOP_HAT(Material.LEATHER_HELMET, "hats/top_hat"),
    HAT_WITCH_HAT(Material.LEATHER_HELMET, "hats/witch_hat"),
    GADGET_GRAPPLING_HOOK(Material.FISHING_ROD, "gadgets/grappling_hook"),
    GADGET_PAINT_GUN(Material.SNOWBALL, "gadgets/paint_gun"),
    PROP_STAR(Material.NETHER_STAR, "props/star");

    private final Material fallback;
    private final String path;
    private final boolean tinted;

    PackModel(Material fallback, String path) {
        this(fallback, path, false);
    }

    /**
     * A tinted model takes the colour of its tinted elements from the item: the first colour of its
     * custom_model_data, as a lightsaber's blade or a blaster's heat vents do.
     */
    PackModel(Material fallback, String path, boolean tinted) {
        this.fallback = fallback;
        this.path = path;
        this.tinted = tinted;
    }

    public Material fallback() {
        return fallback;
    }

    public boolean tinted() {
        return tinted;
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
