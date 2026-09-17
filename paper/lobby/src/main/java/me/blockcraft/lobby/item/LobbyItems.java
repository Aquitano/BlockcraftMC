package me.blockcraft.lobby.item;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** The hotbar; an item built from a {@link MenuIcon} wears the pack's icon once the proxy has said the client has it. */
public final class LobbyItems {

    private static final double HEALTH_SCALE = 6.0;

    private final Messages messages;

    public LobbyItems(Messages messages) {
        this.messages = messages;
    }

    public void give(Player player, boolean playersVisible) {
        var inventory = player.getInventory();
        inventory.clear();
        inventory.setItem(LobbyItem.NAVIGATOR.slot(), item(player, LobbyItem.NAVIGATOR, Items.of(Material.COMPASS), "item.navigator"));
        inventory.setItem(LobbyItem.PLAYER_HIDER.slot(), hiderItem(playersVisible));
        inventory.setItem(LobbyItem.PROFILE.slot(), item(player, LobbyItem.PROFILE, head(player), "item.profile"));
        inventory.setItem(LobbyItem.EXTRAS.slot(), item(player, LobbyItem.EXTRAS, MenuIcon.TOOL_COSMETICS.item(), "item.extras"));
        inventory.setItem(LobbyItem.SETTINGS.slot(), item(player, LobbyItem.SETTINGS, MenuIcon.TOOL_SETTINGS.item(), "item.settings"));
        inventory.setItem(LobbyItem.FRIENDS.slot(), item(player, LobbyItem.FRIENDS, head(player), "item.friends"));
        inventory.setItem(LobbyItem.SECRETS.slot(), item(player, LobbyItem.SECRETS, Items.of(Material.EMERALD), "item.secrets"));
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setHealthScale(HEALTH_SCALE);
    }

    public ItemStack hiderItem(boolean playersVisible) {
        var item = playersVisible
                ? Items.of(Material.LIME_DYE).name(messages.get("item.hider-on"))
                : Items.of(Material.GRAY_DYE).name(messages.get("item.hider-off"));
        return LobbyItem.PLAYER_HIDER.tag(item.lore(messages.get("item.lore")).build());
    }

    /** The real account's skin, so a nicked player still recognises their own head. */
    private static Items.Builder head(Player player) {
        return Items.head(player.getUniqueId(), BlockcraftApi.get().realName(player));
    }

    private ItemStack item(Player player, LobbyItem item, Items.Builder icon, String nameKey) {
        var stack = item.tag(icon.name(messages.get(nameKey)).lore(messages.get("item.lore")).build());
        MenuIcon.style(stack, BlockcraftApi.get().resourcePack().applied(player));
        return stack;
    }
}
