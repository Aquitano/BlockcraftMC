package me.blockcraft.duell.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GameItems {

    private static final double FULL_HEALTH = 20.0;
    private static final int FULL_FOOD = 20;
    private static final int VS_ARROWS = 12;

    private final Messages messages;

    public GameItems(Messages messages) {
        this.messages = messages;
    }

    public void giveLobbyItems(Player player) {
        var inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setItem(0, GameItem.STATS.build(player,
                MenuIcon.TOOL_PROFILE.mark(Items.head(player.getUniqueId(), BlockcraftApi.get().realName(player)))
                        .name(messages.get("item.stats"))));
        inventory.setItem(4, GameItem.MODE.build(player, MenuIcon.DUELL.item().name(messages.get("item.style"))));
        inventory.setItem(8, GameItem.HUB.build(player, MenuIcon.TOOL_HOME.item().name(messages.get("item.hub"))));
        player.setHealth(FULL_HEALTH);
        player.setFoodLevel(FULL_FOOD);
        player.setLevel(0);
        player.setFireTicks(0);
    }

    public void giveVsKit(Player player) {
        var inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setHelmet(named(Material.IRON_HELMET, "item.vs-kit.helmet"));
        inventory.setChestplate(named(Material.IRON_CHESTPLATE, "item.vs-kit.chestplate"));
        inventory.setLeggings(named(Material.IRON_LEGGINGS, "item.vs-kit.leggings"));
        inventory.setBoots(named(Material.IRON_BOOTS, "item.vs-kit.boots"));
        inventory.setItem(0, named(Material.IRON_SWORD, "item.vs-kit.sword"));
        inventory.setItem(1, named(Material.FISHING_ROD, "item.vs-kit.rod"));
        inventory.setItem(7, named(Material.BOW, "item.vs-kit.bow"));
        inventory.setItem(8, Items.of(Material.ARROW, VS_ARROWS).name(messages.get("item.vs-kit.arrows")).build());
        player.setHealth(FULL_HEALTH);
    }

    public void giveSuperJumpKit(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setLevel(0);
        player.setHealth(FULL_HEALTH);
        player.setFoodLevel(FULL_FOOD);
    }

    private ItemStack named(Material material, String key) {
        return Items.of(material).name(messages.get(key)).build();
    }
}
