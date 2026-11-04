package me.blockcraft.quicksg.kit;

import me.blockcraft.api.kit.UnlockableKit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public enum Kit implements UnlockableKit {
    STARTER(Material.WOODEN_SWORD, 0) {
        @Override
        public void apply(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.WOODEN_SWORD));
        }
    },
    BOW(Material.BOW, 200) {
        @Override
        public void apply(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.BOW), ItemStack.of(Material.ARROW, 12));
        }
    },
    HEARTS(Material.REDSTONE, 200) {
        @Override
        public void apply(Player player) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
            player.setHealth(20.0);
        }
    },
    CHAIN(Material.CHAINMAIL_CHESTPLATE, 200) {
        @Override
        public void apply(Player player) {
            player.getInventory().setHelmet(ItemStack.of(Material.CHAINMAIL_HELMET));
            player.getInventory().setLeggings(ItemStack.of(Material.CHAINMAIL_LEGGINGS));
            player.getInventory().setBoots(ItemStack.of(Material.CHAINMAIL_BOOTS));
        }
    },
    DIAMOND(Material.DIAMOND_CHESTPLATE, 200) {
        @Override
        public void apply(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.WOODEN_SWORD));
            var chestplate = ItemStack.of(Material.DIAMOND_CHESTPLATE);
            chestplate.addEnchantment(Enchantment.PROTECTION, 1);
            player.getInventory().setChestplate(chestplate);
        }
    };

    public static final String ITEM_PREFIX = "qsg.kit.";
    private final Material icon;
    private final int defaultPrice;

    Kit(Material icon, int defaultPrice) {
        this.icon = icon;
        this.defaultPrice = defaultPrice;
    }

    public abstract void apply(Player player);

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

    public int defaultPrice() {
        return defaultPrice;
    }

    public String messageKey() {
        return "kit." + key();
    }

}
