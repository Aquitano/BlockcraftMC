package me.blockcraft.ttt.shop;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.ttt.game.Role;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** What the traitors and detectives can buy with their points; each is recognised by its tag, never its material. */
public enum ShopItem implements HotbarItem {
    MEDIPACK(Material.GLISTERING_MELON_SLICE, Set.of(Role.TRAITOR)),
    RADAR(Material.COMPASS, Set.of(Role.TRAITOR, Role.DETECTIVE)),
    CREEPER_ARROWS(Material.ARROW, Set.of(Role.TRAITOR)),
    LANDMINE(Material.STONE_PRESSURE_PLATE, Set.of(Role.TRAITOR)),
    SPOOFER(Material.PAPER, Set.of(Role.TRAITOR)),
    FAKE_CHEST(Material.CHEST, Set.of(Role.TRAITOR)),
    KNIFE(Material.SHEARS, Set.of(Role.TRAITOR)),
    MASK(Material.LEATHER, Set.of(Role.TRAITOR)),
    LIGHTER(Material.FLINT_AND_STEEL, Set.of(Role.TRAITOR)),
    TIME_BOMB(Material.TNT, Set.of(Role.TRAITOR)),
    SILENCED_PISTOL(Material.IRON_HOE, Set.of(Role.TRAITOR)),
    BEAR_TRAP(Material.IRON_TRAPDOOR, Set.of(Role.TRAITOR)),
    POISON(Material.FERMENTED_SPIDER_EYE, Set.of(Role.TRAITOR)),
    JAMMER(Material.LIGHTNING_ROD, Set.of(Role.TRAITOR)),
    HEAL_STATION(Material.SEA_LANTERN, Set.of(Role.DETECTIVE)),
    DNA_SCANNER(Material.BLAZE_ROD, Set.of(Role.DETECTIVE)),
    GOLDEN_SWORD(Material.GOLDEN_SWORD, Set.of(Role.DETECTIVE)),
    MOTION_SENSOR(Material.SCULK_SENSOR, Set.of(Role.DETECTIVE)),
    TASER(Material.BREEZE_ROD, Set.of(Role.DETECTIVE)),
    DEFIBRILLATOR(Material.HEART_OF_THE_SEA, Set.of(Role.DETECTIVE)),
    VEST(Material.IRON_CHESTPLATE, Set.of(Role.DETECTIVE)),
    MINE_DETECTOR(Material.ECHO_SHARD, Set.of(Role.DETECTIVE)),
    CRIME_CAMERA(Material.ENDER_EYE, Set.of(Role.DETECTIVE));

    private final Material material;
    private final Set<Role> sellers;

    ShopItem(Material material, Set<Role> sellers) {
        this.material = material;
        this.sellers = sellers;
    }

    public Material material() {
        return material;
    }

    public String key() {
        return name().toLowerCase().replace('_', '-');
    }

    /** The pack's 3D model of the item, where it has one; without the pack it is the plain material. */
    public Optional<PackModel> model() {
        return Optional.ofNullable(switch (this) {
            case MEDIPACK -> PackModel.TTT_MEDIPACK;
            case RADAR -> PackModel.TTT_RADAR;
            case DNA_SCANNER -> PackModel.TTT_DNA_SCANNER;
            case GOLDEN_SWORD -> PackModel.TTT_GOLDEN_SWORD;
            case KNIFE -> PackModel.TTT_KNIFE;
            case MASK -> PackModel.TTT_MASK;
            case LIGHTER -> PackModel.TTT_LIGHTER;
            case TIME_BOMB -> PackModel.TTT_TIME_BOMB;
            case SILENCED_PISTOL -> PackModel.TTT_SILENCED_PISTOL;
            case BEAR_TRAP -> PackModel.TTT_BEAR_TRAP;
            case POISON -> PackModel.TTT_POISON;
            case JAMMER -> PackModel.TTT_JAMMER;
            case MOTION_SENSOR -> PackModel.TTT_MOTION_SENSOR;
            case TASER -> PackModel.TTT_TASER;
            case DEFIBRILLATOR -> PackModel.TTT_DEFIBRILLATOR;
            case MINE_DETECTOR -> PackModel.TTT_MINE_DETECTOR;
            case CRIME_CAMERA -> PackModel.TTT_CRIME_CAMERA;
            case CREEPER_ARROWS, LANDMINE, SPOOFER, FAKE_CHEST, HEAL_STATION, VEST -> null;
        });
    }

    public static List<ShopItem> soldTo(Role role) {
        return Arrays.stream(values()).filter(item -> item.sellers.contains(role)).toList();
    }
}
