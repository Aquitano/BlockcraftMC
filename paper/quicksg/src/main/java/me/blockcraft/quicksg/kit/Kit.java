package me.blockcraft.quicksg.kit;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public enum Kit {
    STARTER("KIT1", Material.WOODEN_SWORD, true) {
        @Override
        public void apply(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.WOODEN_SWORD));
        }
    },
    BOW("KIT2", Material.BOW, false) {
        @Override
        public void apply(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.BOW), ItemStack.of(Material.ARROW, 12));
        }
    },
    HEARTS("KIT3", Material.REDSTONE, false) {
        @Override
        public void apply(Player player) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
            player.setHealth(20.0);
        }
    },
    CHAIN("KIT4", Material.CHAINMAIL_CHESTPLATE, false) {
        @Override
        public void apply(Player player) {
            player.getInventory().setHelmet(ItemStack.of(Material.CHAINMAIL_HELMET));
            player.getInventory().setLeggings(ItemStack.of(Material.CHAINMAIL_LEGGINGS));
            player.getInventory().setBoots(ItemStack.of(Material.CHAINMAIL_BOOTS));
        }
    },
    DIAMOND("KIT5", Material.DIAMOND_CHESTPLATE, false) {
        @Override
        public void apply(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.WOODEN_SWORD));
            var chestplate = ItemStack.of(Material.DIAMOND_CHESTPLATE);
            chestplate.addEnchantment(Enchantment.PROTECTION, 1);
            player.getInventory().setChestplate(chestplate);
        }
    };

    private final String column;
    private final Material icon;
    private final boolean free;

    Kit(String column, Material icon, boolean free) {
        this.column = column;
        this.icon = icon;
        this.free = free;
    }

    public abstract void apply(Player player);

    public String column() {
        return column;
    }

    public Material icon() {
        return icon;
    }

    public boolean free() {
        return free;
    }

    public String messageKey() {
        return "kit." + name().toLowerCase();
    }

    public static Optional<Kit> bySlot(int slot) {
        return slot >= 0 && slot < values().length ? Optional.of(values()[slot]) : Optional.empty();
    }
}
