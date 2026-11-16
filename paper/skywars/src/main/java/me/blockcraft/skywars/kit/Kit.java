package me.blockcraft.skywars.kit;

import me.blockcraft.api.kit.UnlockableKit;
import me.blockcraft.api.pack.PackModel;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Locale;

/**
 * The kits of GommeHD's SkyWars that this server plays: items to start with, no abilities. A kit's name and what it
 * holds are {@code kit.<key>} in messages.yml, its price {@code kits.<key>} in config.yml.
 */
public enum Kit implements UnlockableKit {
    STANDARD(Material.WOODEN_SWORD) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.WOODEN_SWORD), ItemStack.of(Material.WOODEN_PICKAXE));
        }
    },
    BAUARBEITER(Material.BRICKS) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(PackModel.SKYWARS_HAMMER.item().build(), ItemStack.of(Material.BRICKS, 64));
        }
    },
    BOGENSCHUETZE(Material.BOW) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.BOW), ItemStack.of(Material.ARROW, 12));
        }
    },
    RITTER(Material.CHAINMAIL_CHESTPLATE) {
        @Override
        public void equip(Player player) {
            player.getInventory().setChestplate(ItemStack.of(Material.CHAINMAIL_CHESTPLATE));
            player.getInventory().setLeggings(ItemStack.of(Material.CHAINMAIL_LEGGINGS));
        }
    },
    ENDERMAN(Material.ENDER_PEARL) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.ENDER_PEARL, 2));
        }
    },
    BOMBER(Material.TNT) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.TNT, 3), ItemStack.of(Material.FLINT_AND_STEEL));
        }
    },
    FISCHER(Material.FISHING_ROD) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.FISHING_ROD), ItemStack.of(Material.SNOWBALL, 16));
        }
    },
    HEILER(Material.GOLDEN_APPLE) {
        @Override
        public void equip(Player player) {
            player.getInventory().addItem(healing(), healing(), ItemStack.of(Material.GOLDEN_APPLE));
        }
    };

    private static final String ITEM_PREFIX = "skywars.kit.";

    private final Material icon;

    Kit(Material icon) {
        this.icon = icon;
    }

    public abstract void equip(Player player);

    @Override
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String itemPrefix() {
        return ITEM_PREFIX;
    }

    @Override
    public Material icon() {
        return icon;
    }


    private static ItemStack healing() {
        var potion = ItemStack.of(Material.SPLASH_POTION);
        potion.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(PotionType.HEALING));
        return potion;
    }
}
