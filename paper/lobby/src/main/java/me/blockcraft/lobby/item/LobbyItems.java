package me.blockcraft.lobby.item;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.common.message.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class LobbyItems {

    private static final double HEALTH_SCALE = 6.0;

    private final Messages messages;

    public LobbyItems(Messages messages) {
        this.messages = messages;
    }

    public void give(Player player, boolean playersVisible) {
        var inventory = player.getInventory();
        inventory.clear();
        inventory.setItem(LobbyItem.NAVIGATOR.slot(), item(LobbyItem.NAVIGATOR, Material.COMPASS, "item.navigator"));
        inventory.setItem(LobbyItem.PLAYER_HIDER.slot(), hiderItem(playersVisible));
        inventory.setItem(LobbyItem.PROFILE.slot(), LobbyItem.PROFILE.tag(head(player).name(messages.get("item.profile"))
                .lore(messages.get("item.lore"))
                .build()));
        inventory.setItem(LobbyItem.EXTRAS.slot(), item(LobbyItem.EXTRAS, Material.GLISTERING_MELON_SLICE, "item.extras"));
        inventory.setItem(LobbyItem.SETTINGS.slot(), item(LobbyItem.SETTINGS, Material.COMPARATOR, "item.settings"));
        inventory.setItem(LobbyItem.FRIENDS.slot(), LobbyItem.FRIENDS.tag(head(player).name(messages.get("item.friends"))
                .lore(messages.get("item.lore"))
                .build()));
        inventory.setItem(LobbyItem.SECRETS.slot(), item(LobbyItem.SECRETS, Material.EMERALD, "item.secrets"));
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setHealthScale(HEALTH_SCALE);
    }

    public ItemStack hiderItem(boolean playersVisible) {
        return playersVisible
                ? item(LobbyItem.PLAYER_HIDER, Material.LIME_DYE, "item.hider-on")
                : item(LobbyItem.PLAYER_HIDER, Material.GRAY_DYE, "item.hider-off");
    }

    /** The real account's skin, so a nicked player still recognises their own head. */
    private static Items.Builder head(Player player) {
        return Items.head(player.getUniqueId(), BlockcraftApi.get().realName(player));
    }

    private ItemStack item(LobbyItem item, Material material, String nameKey) {
        return item.tag(Items.of(material).name(messages.get(nameKey)).lore(messages.get("item.lore")).build());
    }
}
