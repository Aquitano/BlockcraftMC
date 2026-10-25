package me.blockcraft.zombies.game;

import org.bukkit.Material;
import me.blockcraft.api.item.HotbarItem;

import java.util.Locale;

public enum Weapon implements HotbarItem {
    PISTOL(Material.WOODEN_HOE),
    RIFLE(Material.IRON_HOE),
    SHOTGUN(Material.STONE_HOE),
    SNIPER(Material.DIAMOND_HOE),
    FLAMETHROWER(Material.GOLDEN_HOE);

    private final Material material;

    Weapon(Material material) {
        this.material = material;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Material material() {
        return material;
    }
}
