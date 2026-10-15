package me.blockcraft.quicksg.kit;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    public static final String ITEM_PREFIX = "qsg.kit.";
    /** The flag columns of the legacy qsg_kits table, for the one-time import into bc_unlocks. */
    public static final Map<String, String> LEGACY_COLUMNS = Arrays.stream(values())
            .filter(kit -> !kit.free)
            .collect(Collectors.toUnmodifiableMap(kit -> kit.legacyColumn, Kit::item));

    private final String legacyColumn;
    private final Material icon;
    private final boolean free;

    Kit(String legacyColumn, Material icon, boolean free) {
        this.legacyColumn = legacyColumn;
        this.icon = icon;
        this.free = free;
    }

    public abstract void apply(Player player);

    public String item() {
        return ITEM_PREFIX + name().toLowerCase();
    }

    public static Set<Kit> ownedAmong(Set<String> items) {
        return Arrays.stream(values()).filter(kit -> kit.free || items.contains(kit.item()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Kit.class)));
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
