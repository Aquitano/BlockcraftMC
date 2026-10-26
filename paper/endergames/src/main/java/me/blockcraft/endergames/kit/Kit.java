package me.blockcraft.endergames.kit;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.common.message.Messages;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;

/**
 * The kits of GommeHD's EnderGames that this server plays: what each starts the round with. What a kit can do
 * beyond its items is {@link KitAbilities}'; its name and description are {@code kit.<key>} in messages.yml.
 */
public enum Kit {
    BARBAR(Material.LEATHER_CHESTPLATE, 0) {
        @Override
        public void equip(Player player, Messages messages) {
            var inventory = player.getInventory();
            inventory.addItem(ItemStack.of(Material.WOODEN_SWORD));
            inventory.setHelmet(ItemStack.of(Material.LEATHER_HELMET));
            inventory.setChestplate(ItemStack.of(Material.LEATHER_CHESTPLATE));
            inventory.setLeggings(ItemStack.of(Material.LEATHER_LEGGINGS));
            inventory.setBoots(ItemStack.of(Material.LEATHER_BOOTS));
        }
    },
    BOGENSCHUETZE(Material.BOW, 0) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.BOW), ItemStack.of(Material.ARROW, 16));
        }
    },
    HOLZFAELLER(Material.WOODEN_AXE, 0) {
        @Override
        public void equip(Player player, Messages messages) {
            var axe = ItemStack.of(Material.WOODEN_AXE);
            axe.addEnchantment(Enchantment.SHARPNESS, 1);
            player.getInventory().addItem(axe);
            player.getInventory().setChestplate(Items.of(Material.LEATHER_CHESTPLATE).color(Color.fromRGB(0xA51D2D)).build());
        }
    },
    JAEGER(Material.STONE_SWORD, 0) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.STONE_SWORD));
            player.getInventory().setChestplate(Items.of(Material.LEATHER_CHESTPLATE).color(Color.fromRGB(0x3B7A2A)).build());
        }
    },
    ENDERMAN(Material.ENDER_PEARL, 500) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.ENDER_PEARL, 5));
        }
    },
    BOMBER(Material.TNT, 500) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.TNT, 5), ItemStack.of(Material.FLINT_AND_STEEL));
        }
    },
    VAMPIR(Material.REDSTONE, 400) {
        @Override
        public void equip(Player player, Messages messages) {
        }
    },
    KAENGURU(Material.RABBIT_FOOT, 500) {
        @Override
        public void equip(Player player, Messages messages) {
            player.setAllowFlight(true);
        }
    },
    SPINNE(Material.COBWEB, 300) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.COBWEB, 16));
            player.getInventory().setHelmet(Items.of(Material.LEATHER_HELMET).color(Color.BLACK).build());
        }
    },
    STOMPER(Material.IRON_BOOTS, 600) {
        @Override
        public void equip(Player player, Messages messages) {
        }
    },
    SKORPION(Material.SPIDER_EYE, 400) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().setChestplate(Items.of(Material.LEATHER_CHESTPLATE).color(Color.GREEN).build());
        }
    },
    THOR(Material.GOLDEN_AXE, 800) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(Items.of(Material.GOLDEN_AXE).name(messages.get("kit.thor.item")).build());
        }
    },
    TELEPORTER(Material.ENDER_EYE, 400) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.ENDER_PEARL, 2));
        }
    },
    BERSERKER(Material.BLAZE_POWDER, 400) {
        @Override
        public void equip(Player player, Messages messages) {
        }
    },
    ANGSTHASE(Material.FEATHER, 300) {
        @Override
        public void equip(Player player, Messages messages) {
            var sword = ItemStack.of(Material.WOODEN_SWORD);
            sword.addEnchantment(Enchantment.KNOCKBACK, 2);
            player.getInventory().addItem(sword);
        }
    },
    SUPPENMEISTER(Material.MUSHROOM_STEW, 500) {
        @Override
        public void equip(Player player, Messages messages) {
            for (int i = 0; i < 6; i++) {
                player.getInventory().addItem(ItemStack.of(Material.MUSHROOM_STEW));
            }
        }
    },
    TRACKER(Material.COMPASS, 300) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(PackModel.ENDERGAMES_TRACKER.item().name(messages.get("kit.tracker.item")).build());
        }
    };

    public static final String ITEM_PREFIX = "endergames.kit.";

    private final Material icon;
    private final int defaultPrice;

    Kit(Material icon, int defaultPrice) {
        this.icon = icon;
        this.defaultPrice = defaultPrice;
    }

    public abstract void equip(Player player, Messages messages);

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
}
