package me.blockcraft.skywars.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Locale;
import java.util.Optional;

/**
 * The kits of GommeHD's SkyWars that this server plays: items to start with, no abilities. A kit's name and what it
 * holds are {@code kit.<key>} in messages.yml, its price {@code kits.<key>} in config.yml.
 */
public enum Kit {
    STANDARD(Material.WOODEN_SWORD, 0) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.WOODEN_SWORD), ItemStack.of(Material.WOODEN_PICKAXE));
        }
    },
    BAUARBEITER(Material.BRICKS, 300) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.STONE_PICKAXE), ItemStack.of(Material.BRICKS, 64));
        }
    },
    BOGENSCHUETZE(Material.BOW, 400) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.BOW), ItemStack.of(Material.ARROW, 12));
        }
    },
    RITTER(Material.CHAINMAIL_CHESTPLATE, 500) {
        @Override
        public void equip(Player player) {
            player.getInventory().setChestplate(ItemStack.of(Material.CHAINMAIL_CHESTPLATE));
            player.getInventory().setLeggings(ItemStack.of(Material.CHAINMAIL_LEGGINGS));
        }
    },
    ENDERMAN(Material.ENDER_PEARL, 500) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.ENDER_PEARL, 2));
        }
    },
    BOMBER(Material.TNT, 400) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.TNT, 3), ItemStack.of(Material.FLINT_AND_STEEL));
        }
    },
    FISCHER(Material.FISHING_ROD, 300) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.FISHING_ROD), ItemStack.of(Material.SNOWBALL, 16));
        }
    },
    HEILER(Material.GOLDEN_APPLE, 500) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(healing(), healing(), ItemStack.of(Material.GOLDEN_APPLE));
        }
    };

    public static final String ITEM_PREFIX = "skywars.kit.";

    private final Material icon;
    private final int defaultPrice;

    Kit(Material icon, int defaultPrice) {
        this.icon = icon;
        this.defaultPrice = defaultPrice;
    }

    public abstract void equip(Player player);

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The row in bc_unlocks that says a player bought this kit. */
    public String item() {
        return ITEM_PREFIX + key();
    }

    public Material icon() {
        return icon;
    }

    public int defaultPrice() {
        return defaultPrice;
    }

    public static Optional<Kit> byItem(String item) {
        if (!item.startsWith(ITEM_PREFIX)) {
            return Optional.empty();
        }
        var key = item.substring(ITEM_PREFIX.length());
        for (var kit : values()) {
            if (kit.key().equals(key)) {
                return Optional.of(kit);
            }
        }
        return Optional.empty();
    }

    private static ItemStack healing() {
        var potion = ItemStack.of(Material.SPLASH_POTION);
        potion.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.HEALING));
        return potion;
    }
}
