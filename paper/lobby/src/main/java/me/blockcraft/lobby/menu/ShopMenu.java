package me.blockcraft.lobby.menu;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.shop.Purchases;
import me.blockcraft.api.task.Tasks;
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
    private enum State { AVAILABLE, ACTIVATING, OWNED, RETRY }

    private State state;

    ShopMenu(Menus menus, Player viewer) {
        super(2, menus.messages().get("shop.title"));
        this.menus = menus;
        state = viewer.hasPermission(LobbyPermissions.PREMIUM) ? State.OWNED : State.AVAILABLE;
        var messages = menus.messages();
        closeButton(ToolbarButton.LEFT);
        toolbar(ToolbarButton.MIDDLE, MenuIcon.TOOL_HEADS.item()
                .name(messages.get("shop.heads")).lore(messages.get("shop.heads-lore")).build(), menus::openHeads);
        toolbar(ToolbarButton.RIGHT, MenuIcon.TOOL_SHIRTS.item().color(SHIRT_ICON_COLOR)
                .name(messages.get("shop.shirts")).lore(messages.get("shop.shirts-lore")).build(), menus::openShirts);
        refresh();
    }

    private void refresh() {
        var messages = menus.messages();
        if (state == State.OWNED) {
            info(PREMIUM_SLOT, MenuIcon.PREMIUM.item().name(messages.get("shop.premium"))
                    .lore(messages.get("shop.premium-owned")).glow().build());
        } else if (state == State.ACTIVATING) {
            info(PREMIUM_SLOT, MenuIcon.PREMIUM.item().name(messages.get("shop.premium"))
                    .lore(messages.get("shop.premium-activating")).build());
        } else {
            set(PREMIUM_SLOT, premiumIcon());
        }
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        if (slot != PREMIUM_SLOT) {
            return true;
        }
        if (player.hasPermission(LobbyPermissions.PREMIUM)) {
            state = State.OWNED;
            refresh();
            return false;
        }
        return state == State.AVAILABLE || state == State.RETRY;
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
        if (state == State.RETRY) {
            activate(player);
            return;
        }
        Purchases.confirm(player, premiumIcon(), PREMIUM_ITEM, menus.config().premiumPrice(), this::activate, this::open);
    }

    private void activate(Player buyer) {
        state = State.ACTIVATING;
        refresh();
        menus.premiumRank().grant(buyer.getUniqueId()).thenAccept(granted -> {
            if (menus.plugin().isEnabled()) {
                Tasks.sync(menus.plugin(), () -> {
                    state = granted ? State.OWNED : State.RETRY;
                    refresh();
                    if (!granted && buyer.isOnline()) {
                        buyer.sendMessage(menus.messages().get("shop.premium-grant-failed"));
                    }
                });
            }
        });
    }

    private ItemStack premiumIcon() {
        var messages = menus.messages();
        return MenuIcon.PREMIUM.item()
                .name(messages.get("shop.premium"))
                .lore(messages.get("shop.premium-lore"),
                        state == State.RETRY ? messages.get("shop.premium-retry")
                                : messages.get("shop.price-lore", Formatter.number("coins", menus.config().premiumPrice())))
                .build();
    }
}
