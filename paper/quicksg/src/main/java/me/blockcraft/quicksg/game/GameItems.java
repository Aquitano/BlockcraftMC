package me.blockcraft.quicksg.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.common.message.Messages;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class GameItems {

    private final Messages messages;

    public GameItems(Messages messages) {
        this.messages = messages;
    }

    public ItemStack kits() {
        return Items.of(Material.CHEST).name(messages.get("item.kits")).build();
    }

    public ItemStack hub() {
        return Items.of(Material.SLIME_BALL).name(messages.get("item.hub")).build();
    }

    public ItemStack stats() {
        return Items.of(Material.PLAYER_HEAD).name(messages.get("item.stats")).build();
    }

    public ItemStack start() {
        return Items.of(Material.DIAMOND).name(messages.get("item.start")).build();
    }

    public ItemStack tracker() {
        return named(Material.COMPASS, 1, "item.tracker");
    }

    public ItemStack head(String owner, String nameKey) {
        return Items.head(owner).name(messages.get(nameKey)).build();
    }

    /** Chest loot: the consumable items carry a name and a hint, everything else is a plain stack. */
    public ItemStack loot(Material material, int amount) {
        return switch (material) {
            case ENCHANTED_BOOK -> named(material, amount, "item.level-up");
            case MUSHROOM_STEW -> named(material, amount, "item.heart");
            case CAKE -> named(material, amount, "item.full-food");
            case COMPASS -> named(material, amount, "item.tracker");
            default -> ItemStack.of(material, amount);
        };
    }

    private ItemStack named(Material material, int amount, String key) {
        return Items.of(material, amount)
                .name(messages.get(key + ".name"))
                .lore(messages.get(key + ".lore"))
                .build();
    }
}
