package me.blockcraft.quicksg.kit;

import me.blockcraft.api.kit.UnlockableKit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public enum Kit implements UnlockableKit {
    STARTER(Material.WOODEN_SWORD) {
        @Override
        public void apply(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.WOODEN_SWORD));
        }
    },
    BOW(Material.BOW) {
        @Override
        public void apply(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.BOW), ItemStack.of(Material.ARROW, 12));
        }
    },
    HEARTS(Material.REDSTONE) {
        @Override
        public void apply(Player player) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
            player.setHealth(20.0);
        }
    },
    CHAIN(Material.CHAINMAIL_CHESTPLATE) {
        @Override
        public void apply(Player player) {
            player.getInventory().setHelmet(ItemStack.of(Material.CHAINMAIL_HELMET));
            player.getInventory().setLeggings(ItemStack.of(Material.CHAINMAIL_LEGGINGS));
            player.getInventory().setBoots(ItemStack.of(Material.CHAINMAIL_BOOTS));
        }
    },
    DIAMOND(Material.DIAMOND_CHESTPLATE) {
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

    Kit(Material icon) {
        this.icon = icon;
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


    public String messageKey() {
        return "kit." + key();
    }

}
