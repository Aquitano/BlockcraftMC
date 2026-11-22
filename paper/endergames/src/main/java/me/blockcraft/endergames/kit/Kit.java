package me.blockcraft.endergames.kit;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.kit.UnlockableKit;
import me.blockcraft.api.pack.PackModel;
import me.blockcraft.common.message.Messages;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * The kits of GommeHD's EnderGames that this server plays: what each starts the round with. What a kit can do
 * beyond its items is {@link KitAbilities}'; its name and description are {@code kit.<key>} in messages.yml.
 */
public enum Kit implements UnlockableKit {
    BARBAR(Material.LEATHER_CHESTPLATE) {
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
    BOGENSCHUETZE(Material.BOW) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.BOW), ItemStack.of(Material.ARROW, 16));
        }
    },
    HOLZFAELLER(Material.WOODEN_AXE) {
        @Override
        public void equip(Player player, Messages messages) {
            var axe = ItemStack.of(Material.WOODEN_AXE);
            axe.addEnchantment(Enchantment.SHARPNESS, 1);
            player.getInventory().addItem(axe);
            player.getInventory().setChestplate(Items.of(Material.LEATHER_CHESTPLATE).color(Color.fromRGB(0xA51D2D)).build());
        }
    },
    JAEGER(Material.STONE_SWORD) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.STONE_SWORD));
            player.getInventory().setChestplate(Items.of(Material.LEATHER_CHESTPLATE).color(Color.fromRGB(0x3B7A2A)).build());
        }
    },
    ENDERMAN(Material.ENDER_PEARL) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.ENDER_PEARL, 5));
        }
    },
    BOMBER(Material.TNT) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.TNT, 5), ItemStack.of(Material.FLINT_AND_STEEL));
        }
    },
    VAMPIR(Material.REDSTONE) {
        @Override
        public void equip(Player player, Messages messages) {
        }
    },
    KAENGURU(Material.RABBIT_FOOT) {
        @Override
        public void equip(Player player, Messages messages) {
            player.setAllowFlight(true);
        }
    },
    SPINNE(Material.COBWEB) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.COBWEB, 16));
            player.getInventory().setHelmet(Items.of(Material.LEATHER_HELMET).color(Color.BLACK).build());
        }
    },
    STOMPER(Material.IRON_BOOTS) {
        @Override
        public void equip(Player player, Messages messages) {
        }
    },
    SKORPION(Material.SPIDER_EYE) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().setChestplate(Items.of(Material.LEATHER_CHESTPLATE).color(Color.GREEN).build());
        }
    },
    THOR(Material.GOLDEN_AXE) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(Items.of(Material.GOLDEN_AXE).name(messages.get("kit.thor.item")).build());
        }
    },
    TELEPORTER(Material.ENDER_EYE) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(ItemStack.of(Material.ENDER_PEARL, 2));
        }
    },
    BERSERKER(Material.BLAZE_POWDER) {
        @Override
        public void equip(Player player, Messages messages) {
        }
    },
    ANGSTHASE(Material.FEATHER) {
        @Override
        public void equip(Player player, Messages messages) {
            var sword = ItemStack.of(Material.WOODEN_SWORD);
            sword.addEnchantment(Enchantment.KNOCKBACK, 2);
            player.getInventory().addItem(sword);
        }
    },
    SUPPENMEISTER(Material.MUSHROOM_STEW) {
        @Override
        public void equip(Player player, Messages messages) {
            for (int i = 0; i < 6; i++) {
                player.getInventory().addItem(ItemStack.of(Material.MUSHROOM_STEW));
            }
        }
    },
    TRACKER(Material.COMPASS) {
        @Override
        public void equip(Player player, Messages messages) {
            player.getInventory().addItem(PackModel.ENDERGAMES_TRACKER.item().name(messages.get("kit.tracker.item")).build());
        }
    };

    private static final String ITEM_PREFIX = "endergames.kit.";

    private final Material icon;

    Kit(Material icon) {
        this.icon = icon;
    }

    public abstract void equip(Player player, Messages messages);

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


}
