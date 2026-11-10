package me.blockcraft.rush.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.config.RushConfig;
import me.blockcraft.rush.kit.Perk;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The round shop's stock per category, priced by {@code shop-prices} in config.yml; perk items only appear for
 * players who unlocked the perk.
 */
final class ShopCatalog {

    private final Messages messages;
    private final RushConfig config;

    ShopCatalog(Messages messages, RushConfig config) {
        this.messages = messages;
        this.config = config;
    }

    List<ShopEntry> entries(ShopCategory category, Set<Perk> ownedPerks) {
        return switch (category) {
            case CHAIN -> List.of(
                    item(9, "chain-helmet", ItemStack.of(Material.CHAINMAIL_HELMET)),
                    item(10, "chain-chestplate", ItemStack.of(Material.CHAINMAIL_CHESTPLATE)),
                    item(11, "chain-leggings", ItemStack.of(Material.CHAINMAIL_LEGGINGS)),
                    item(12, "chain-boots", ItemStack.of(Material.CHAINMAIL_BOOTS)));
            case IRON -> List.of(
                    item(9, "iron-helmet", ItemStack.of(Material.IRON_HELMET)),
                    item(10, "iron-chestplate", ItemStack.of(Material.IRON_CHESTPLATE)),
                    item(11, "iron-leggings", ItemStack.of(Material.IRON_LEGGINGS)),
                    item(12, "iron-boots", ItemStack.of(Material.IRON_BOOTS)));
            case WEAPONS -> List.of(
                    item(9, "stone-sword", ItemStack.of(Material.STONE_SWORD)),
                    item(10, "iron-sword", ItemStack.of(Material.IRON_SWORD)),
                    item(11, "diamond-sword", ItemStack.of(Material.DIAMOND_SWORD)),
                    item(13, "bow", ItemStack.of(Material.BOW)),
                    item(14, "arrows", ItemStack.of(Material.ARROW, 5)));
            case POTIONS -> List.of(
                    item(10, "jump", potion(Material.POTION, PotionType.STRONG_LEAPING, 1)),
                    item(12, "heal-1", potion(Material.SPLASH_POTION, PotionType.HEALING, 3)),
                    item(13, "heal-2", potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 3)),
                    item(15, "fire-resistance", potion(Material.POTION, PotionType.FIRE_RESISTANCE, 1)),
                    item(16, "speed", potion(Material.POTION, PotionType.SWIFTNESS, 1)));
            case SPECIAL -> special(ownedPerks);
        };
    }

    private List<ShopEntry> special(Set<Perk> ownedPerks) {
        var entries = new ArrayList<ShopEntry>();
        entries.add(entry(9, "extra-heart", ItemStack.of(Material.POPPY), player -> {
            var maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH));
            maxHealth.setBaseValue(maxHealth.getBaseValue() + 2.0);
            player.setHealth(player.getHealth() + 2.0);
        }));
        entries.add(item(10, "golden-apple", ItemStack.of(Material.GOLDEN_APPLE)));
        entries.add(entry(11, "level", ItemStack.of(Material.ENCHANTED_BOOK),
                player -> player.setLevel(player.getLevel() + 1)));
        entries.add(item(12, "lapis", ItemStack.of(Material.LAPIS_LAZULI)));
        if (ownedPerks.contains(Perk.ANGEL)) {
            entries.add(item(14, "angel", ItemStack.of(Material.FISHING_ROD)));
        }
        if (ownedPerks.contains(Perk.LUCKY_POTION)) {
            entries.add(item(15, "lucky-potion", potion(Material.POTION, PotionType.INVISIBILITY, 1)));
        }
        if (ownedPerks.contains(Perk.STRENGTH)) {
            entries.add(item(16, "strength", potion(Material.POTION, PotionType.STRENGTH, 1)));
        }
        return entries;
    }

    private ShopEntry item(int slot, String key, ItemStack reward) {
        return entry(slot, key, reward, player -> hasCapacity(player, reward),
                player -> player.getInventory().addItem(reward.clone()));
    }

    private static boolean hasCapacity(Player player, ItemStack item) {
        int remaining = item.getAmount();
        int limit = Math.min(item.getMaxStackSize(), player.getInventory().getMaxStackSize());
        for (var stored : player.getInventory().getStorageContents()) {
            if (stored == null || stored.isEmpty()) {
                remaining -= limit;
            } else if (stored.isSimilar(item)) {
                remaining -= Math.max(0, limit - stored.getAmount());
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private ShopEntry entry(int slot, String key, ItemStack base, Consumer<Player> reward) {
        return entry(slot, key, base, player -> true, reward);
    }

    private ShopEntry entry(int slot, String key, ItemStack base, Predicate<Player> hasCapacity, Consumer<Player> reward) {
        int price = config.shopPrice(key);
        var icon = Items.of(base.clone())
                .name(messages.get("shop.item." + key))
                .lore(messages.get("shop.price", Formatter.number("price", price)))
                .build();
        return new ShopEntry(slot, icon, price, hasCapacity, reward);
    }

    private static ItemStack potion(Material material, PotionType type, int amount) {
        var stack = ItemStack.of(material, amount);
        stack.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
        return stack;
    }
}
