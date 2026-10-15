package me.blockcraft.quicksg.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GameItems {

    private final Messages messages;

    public GameItems(Messages messages) {
        this.messages = messages;
    }

    public ItemStack hotbar(Player viewer, GameItem item) {
        return item.build(viewer, switch (item) {
            case STATS -> MenuIcon.TOOL_PROFILE.mark(Items.head(viewer.getUniqueId(), BlockcraftApi.get().realName(viewer)))
                    .name(messages.get("item.stats"));
            case KITS -> MenuIcon.QUICKSG.item().name(messages.get("item.kits"));
            case START -> MenuIcon.START.item().name(messages.get("item.start"));
            case HUB -> MenuIcon.TOOL_HOME.item().name(messages.get("item.hub"));
        });
    }

    public ItemStack tracker() {
        return named(Material.COMPASS, 1, "item.tracker");
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
