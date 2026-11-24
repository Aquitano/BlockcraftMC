package me.blockcraft.duell.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.common.message.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GameItems {

    private static final int VS_ARROWS = 12;

    private final Messages messages;

    public GameItems(Messages messages) {
        this.messages = messages;
    }

    /** Into an inventory that {@link me.blockcraft.api.game.PlayerReset} has emptied. */
    public void giveVsKit(Player player) {
        var inventory = player.getInventory();
        inventory.setHelmet(named(Material.IRON_HELMET, "item.vs-kit.helmet"));
        inventory.setChestplate(named(Material.IRON_CHESTPLATE, "item.vs-kit.chestplate"));
        inventory.setLeggings(named(Material.IRON_LEGGINGS, "item.vs-kit.leggings"));
        inventory.setBoots(named(Material.IRON_BOOTS, "item.vs-kit.boots"));
        inventory.setItem(0, named(Material.IRON_SWORD, "item.vs-kit.sword"));
        inventory.setItem(1, named(Material.FISHING_ROD, "item.vs-kit.rod"));
        inventory.setItem(7, named(Material.BOW, "item.vs-kit.bow"));
        inventory.setItem(8, Items.of(Material.ARROW, VS_ARROWS).name(messages.get("item.vs-kit.arrows")).build());
    }

    private ItemStack named(Material material, String key) {
        return Items.of(material).name(messages.get(key)).build();
    }
}
