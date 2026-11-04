package me.blockcraft.skywars.map;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The maps SkyWars builds, each the same island chain in its own look: what the islands are made of and what grows
 * on them. Each draws its shapes from its own seed, so a map is the same at every start. The name is
 * {@code map.<key>} in messages.yml.
 */
public enum Theme {
    WOLKENHAIN(0x5EA1L, Material.OAK_SAPLING, Material.GRASS_BLOCK, Material.DIRT, Material.STONE,
            List.of(Material.COAL_ORE, Material.IRON_ORE, Material.ANDESITE), Material.GLASS,
            Material.STRIPPED_OAK_LOG, Material.OAK_SLAB),
    FROSTSPITZE(0xF205L, Material.SNOWBALL, Material.SNOW_BLOCK, Material.PACKED_ICE, Material.STONE,
            List.of(Material.IRON_ORE, Material.DIORITE, Material.LAPIS_ORE), Material.LIGHT_BLUE_STAINED_GLASS,
            Material.BLUE_ICE, Material.SPRUCE_SLAB),
    WUESTENWIND(0xD3E7L, Material.DEAD_BUSH, Material.SAND, Material.SANDSTONE, Material.TERRACOTTA,
            List.of(Material.ORANGE_TERRACOTTA, Material.RED_TERRACOTTA, Material.GOLD_ORE), Material.ORANGE_STAINED_GLASS,
            Material.CHISELED_SANDSTONE, Material.SMOOTH_SANDSTONE_SLAB);

    private final long seed;
    private final Material icon;
    private final Material top;
    private final Material soil;
    private final Material stone;
    private final List<Material> veins;
    private final Material cage;
    private final Material pillar;
    private final Material roof;

    Theme(long seed, Material icon, Material top, Material soil, Material stone, List<Material> veins, Material cage,
          Material pillar, Material roof) {
        this.seed = seed;
        this.icon = icon;
        this.top = top;
        this.soil = soil;
        this.stone = stone;
        this.veins = veins;
        this.cage = cage;
        this.pillar = pillar;
        this.roof = roof;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Theme> byKey(String key) {
        for (var theme : values()) {
            if (theme.key().equalsIgnoreCase(key)) {
                return Optional.of(theme);
            }
        }
        return Optional.empty();
    }

    long seed() {
        return seed;
    }

    public Material icon() {
        return icon;
    }

    Material top() {
        return top;
    }

    Material soil() {
        return soil;
    }

    Material stone() {
        return stone;
    }

    List<Material> veins() {
        return veins;
    }

    Material cage() {
        return cage;
    }

    Material pillar() {
        return pillar;
    }

    Material roof() {
        return roof;
    }
}
