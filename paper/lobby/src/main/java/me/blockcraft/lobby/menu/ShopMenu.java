package me.blockcraft.lobby.menu;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.lobby.LobbyPermissions;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** The toolbar closes or opens one of the two catalogues; Premium is the one item sold right here. */
public final class ShopMenu extends Menu {

    private static final int PREMIUM_SLOT = 13;
    public static final String PREMIUM_ITEM = "lobby.premium";
    private static final Color SHIRT_ICON_COLOR = Color.fromRGB(0, 102, 204);

    private final Menus menus;

    ShopMenu(Menus menus) {
        super(2, menus.messages().get("shop.title"));
        this.menus = menus;
        var messages = menus.messages();
        closeButton(ToolbarButton.LEFT);
        toolbar(ToolbarButton.MIDDLE, MenuIcon.TOOL_HEADS.item()
                .name(messages.get("shop.heads")).lore(messages.get("shop.heads-lore")).build(), menus::openHeads);
        toolbar(ToolbarButton.RIGHT, MenuIcon.TOOL_SHIRTS.item().color(SHIRT_ICON_COLOR)
                .name(messages.get("shop.shirts")).lore(messages.get("shop.shirts-lore")).build(), menus::openShirts);
        set(PREMIUM_SLOT, premiumIcon());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == PREMIUM_SLOT) {
            buyPremium(player);
        } else {
            close(player);
        }
    }

    private void buyPremium(Player player) {
        if (player.hasPermission(LobbyPermissions.PREMIUM)) {
            close(player);
            player.sendMessage(menus.messages().get("shop.already-premium"));
            return;
        }
        menus.purchase(player, premiumIcon(), PREMIUM_ITEM, menus.config().premiumPrice(),
                buyer -> menus.premiumRank().grant(buyer.getUniqueId()));
    }

    private ItemStack premiumIcon() {
        var messages = menus.messages();
        return MenuIcon.PREMIUM.item()
                .name(messages.get("shop.premium"))
                .lore(messages.get("shop.premium-lore"),
                        messages.get("shop.price-lore", Formatter.number("coins", menus.config().premiumPrice())))
                .build();
    }
}
