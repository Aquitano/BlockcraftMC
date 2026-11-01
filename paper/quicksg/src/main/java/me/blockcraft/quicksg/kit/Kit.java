package me.blockcraft.quicksg.kit;

import me.blockcraft.api.kit.UnlockableKit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public enum Kit implements UnlockableKit {
    STARTER(Material.WOODEN_SWORD, true) {
        @Override
        public void apply(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.WOODEN_SWORD));
        }
    },
    BOW(Material.BOW, false) {
        @Override
        public void apply(Player player) {
            player.getInventory().addItem(ItemStack.of(Material.BOW), ItemStack.of(Material.ARROW, 12));
        }
    },
    HEARTS(Material.REDSTONE, false) {
        @Override
        public void apply(Player player) {
            player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
            player.setHealth(20.0);
        }
    },
    CHAIN(Material.CHAINMAIL_CHESTPLATE, false) {
        @Override
        public void apply(Player player) {
            player.getInventory().setHelmet(ItemStack.of(Material.CHAINMAIL_HELMET));
            player.getInventory().setLeggings(ItemStack.of(Material.CHAINMAIL_LEGGINGS));
            player.getInventory().setBoots(ItemStack.of(Material.CHAINMAIL_BOOTS));
        }
    },
    DIAMOND(Material.DIAMOND_CHESTPLATE, false) {
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
    private final boolean free;

    Kit(Material icon, boolean free) {
        this.icon = icon;
        this.free = free;
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

    public static Set<Kit> ownedAmong(Set<String> items) {
        return Arrays.stream(values()).filter(kit -> kit.free || items.contains(kit.item()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Kit.class)));
    }

    @Override
    public Material icon() {
        return icon;
    }

    public boolean free() {
        return free;
    }

    public String messageKey() {
        return "kit." + key();
    }

    public static Optional<Kit> bySlot(int slot) {
        return slot >= 0 && slot < values().length ? Optional.of(values()[slot]) : Optional.empty();
    }
}
