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
    HEAL_STATION(Material.SEA_LANTERN, Set.of(Role.DETECTIVE)),
    DNA_SCANNER(Material.BLAZE_ROD, Set.of(Role.DETECTIVE)),
    GOLDEN_SWORD(Material.GOLDEN_SWORD, Set.of(Role.DETECTIVE));

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
        return switch (this) {
            case MEDIPACK -> Optional.of(PackModel.TTT_MEDIPACK);
            case RADAR -> Optional.of(PackModel.TTT_RADAR);
            case DNA_SCANNER -> Optional.of(PackModel.TTT_DNA_SCANNER);
            case GOLDEN_SWORD -> Optional.of(PackModel.TTT_GOLDEN_SWORD);
            default -> Optional.empty();
        };
    }

    public static List<ShopItem> soldTo(Role role) {
        return Arrays.stream(values()).filter(item -> item.sellers.contains(role)).toList();
    }
}
