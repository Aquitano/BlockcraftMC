package me.blockcraft.rush.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
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

/** The round shop's stock per category; perk items only appear for players who unlocked the perk. */
final class ShopCatalog {

    private final Messages messages;

    ShopCatalog(Messages messages) {
        this.messages = messages;
    }

    List<ShopEntry> entries(ShopCategory category, Set<Perk> ownedPerks) {
        return switch (category) {
            case CHAIN -> List.of(
                    item(9, "chain-helmet", ItemStack.of(Material.CHAINMAIL_HELMET), 50),
                    item(10, "chain-chestplate", ItemStack.of(Material.CHAINMAIL_CHESTPLATE), 50),
                    item(11, "chain-leggings", ItemStack.of(Material.CHAINMAIL_LEGGINGS), 50),
                    item(12, "chain-boots", ItemStack.of(Material.CHAINMAIL_BOOTS), 50));
            case IRON -> List.of(
                    item(9, "iron-helmet", ItemStack.of(Material.IRON_HELMET), 80),
                    item(10, "iron-chestplate", ItemStack.of(Material.IRON_CHESTPLATE), 80),
                    item(11, "iron-leggings", ItemStack.of(Material.IRON_LEGGINGS), 80),
                    item(12, "iron-boots", ItemStack.of(Material.IRON_BOOTS), 80));
            case WEAPONS -> List.of(
                    item(9, "stone-sword", ItemStack.of(Material.STONE_SWORD), 30),
                    item(10, "iron-sword", ItemStack.of(Material.IRON_SWORD), 45),
                    item(11, "diamond-sword", ItemStack.of(Material.DIAMOND_SWORD), 60),
                    item(13, "bow", ItemStack.of(Material.BOW), 40),
                    item(14, "arrows", ItemStack.of(Material.ARROW, 5), 20));
            case POTIONS -> List.of(
                    item(10, "jump", potion(Material.POTION, PotionType.STRONG_LEAPING, 1), 100),
                    item(12, "heal-1", potion(Material.SPLASH_POTION, PotionType.HEALING, 3), 100),
                    item(13, "heal-2", potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 3), 200),
                    item(15, "fire-resistance", potion(Material.POTION, PotionType.FIRE_RESISTANCE, 1), 100),
                    item(16, "speed", potion(Material.POTION, PotionType.SWIFTNESS, 1), 100));
            case SPECIAL -> special(ownedPerks);
        };
    }

    private List<ShopEntry> special(Set<Perk> ownedPerks) {
        var entries = new ArrayList<ShopEntry>();
        entries.add(entry(9, "extra-heart", ItemStack.of(Material.POPPY), 40, player -> {
            var maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH));
            maxHealth.setBaseValue(maxHealth.getBaseValue() + 2.0);
            player.setHealth(player.getHealth() + 2.0);
        }));
        entries.add(item(10, "golden-apple", ItemStack.of(Material.GOLDEN_APPLE), 35));
        entries.add(entry(11, "level", ItemStack.of(Material.ENCHANTED_BOOK), 30, player -> {
            player.setLevel(player.getLevel() + 1);
            Sounds.LEVEL_UP.play(player);
        }));
        entries.add(item(12, "lapis", ItemStack.of(Material.LAPIS_LAZULI), 20));
        if (ownedPerks.contains(Perk.ANGEL)) {
            entries.add(item(14, "angel", ItemStack.of(Material.FISHING_ROD), 100));
        }
        if (ownedPerks.contains(Perk.LUCKY_POTION)) {
            entries.add(item(15, "lucky-potion", potion(Material.POTION, PotionType.INVISIBILITY, 1), 400));
        }
        if (ownedPerks.contains(Perk.STRENGTH)) {
            entries.add(item(16, "strength", potion(Material.POTION, PotionType.STRENGTH, 1), 500));
        }
        return entries;
    }

    private ShopEntry item(int slot, String key, ItemStack reward, int price) {
        return entry(slot, key, reward, price, player -> player.getInventory().addItem(reward.clone()));
    }

    private ShopEntry entry(int slot, String key, ItemStack base, int price, Consumer<Player> reward) {
        var icon = Items.of(base.clone())
                .name(messages.get("shop.item." + key))
                .lore(messages.get("shop.price", Formatter.number("price", price)))
                .build();
        return new ShopEntry(slot, icon, price, reward);
    }

    private static ItemStack potion(Material material, PotionType type, int amount) {
        var stack = ItemStack.of(material, amount);
        stack.editMeta(PotionMeta.class, meta -> meta.setBasePotionType(type));
        return stack;
    }
}
