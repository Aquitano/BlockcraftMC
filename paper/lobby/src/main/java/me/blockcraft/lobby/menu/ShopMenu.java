package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.MenuTile;
import me.blockcraft.api.pack.MenuState;
import me.blockcraft.api.shop.Purchases;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.shop.Premium;
import me.blockcraft.lobby.LobbyPermissions;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** Coin catalogues and Premium, beside the permanent rewards earned through levels. */
public final class ShopMenu extends Menu {

    private static final int HEADS_SLOT = 9;
    private static final int HATS_SLOT = 12;
    private static final int SHIRTS_SLOT = 15;
    private static final int KILL_EFFECTS_SLOT = 27;
    private static final int WIN_EFFECTS_SLOT = 30;
    private static final int BALANCE_SLOT = 45;
    private static final int PREMIUM_SLOT = 33;
    private static final int LEVEL_SLOT = 50;
    private static final Color SHIRT_ICON_COLOR = Color.fromRGB(0, 102, 204);

    private final Menus menus;
    private enum State { AVAILABLE, ACTIVATING, OWNED, RETRY }

    private State state;
    private int balance;

    ShopMenu(Menus menus, Player viewer, int balance) {
        super(6, menus.messages().get("shop.title"));
        this.menus = menus;
        this.balance = balance;
        state = viewer.hasPermission(LobbyPermissions.PREMIUM) ? State.OWNED : State.AVAILABLE;
        var messages = menus.messages();
        closeButton(ToolbarButton.LEFT);
        toolbar(ToolbarButton.MIDDLE, MenuIcon.TOOL_PROFILE.item()
                .name(messages.get("shop.profile")).lore(messages.get("shop.profile-lore")).build(), menus::openProfile);
        toolbar(ToolbarButton.RIGHT, MenuIcon.TOOL_COSMETICS.item()
                .name(messages.get("shop.cosmetics")).lore(messages.get("shop.cosmetics-lore")).build(), menus::openExtras);
        tile(HEADS_SLOT, MenuTile.HEADS, MenuTile.Shape.LARGE, MenuIcon.TOOL_HEADS.item()
                .name(messages.get("shop.heads")).lore(messages.get("shop.heads-lore")).build());
        tile(HATS_SLOT, MenuTile.HATS, MenuTile.Shape.LARGE, MenuIcon.TOOL_HATS.item()
                .name(messages.get("shop.hats")).lore(messages.get("shop.hats-lore")).build());
        tile(SHIRTS_SLOT, MenuTile.SHIRTS, MenuTile.Shape.LARGE, MenuIcon.TOOL_SHIRTS.item().color(SHIRT_ICON_COLOR)
                .name(messages.get("shop.shirts")).lore(messages.get("shop.shirts-lore")).build());
        tile(KILL_EFFECTS_SLOT, MenuTile.KILL_EFFECTS, MenuTile.Shape.LARGE, MenuIcon.KILL_EFFECTS.item()
                .name(messages.get("shop.kill-effects")).lore(messages.get("shop.effects-lore")).build());
        tile(WIN_EFFECTS_SLOT, MenuTile.WIN_EFFECTS, MenuTile.Shape.LARGE, MenuIcon.WIN_EFFECTS.item()
                .name(messages.get("shop.win-effects")).lore(messages.get("shop.effects-lore")).build());
        tile(LEVEL_SLOT, MenuTile.LEVEL, MenuTile.Shape.WIDE, MenuIcon.ACHIEVEMENTS.item()
                .name(messages.get("shop.level")).lore(messages.getLines("shop.level-lore")).build());
        tile(BALANCE_SLOT, MenuTile.COINS, MenuTile.Shape.WIDE, balanceIcon());
        tile(PREMIUM_SLOT, MenuTile.PREMIUM, MenuTile.Shape.LARGE, premiumIcon());
        refresh();
    }

    private void refresh() {
        var messages = menus.messages();
        info(BALANCE_SLOT, balanceIcon());
        if (state == State.OWNED) {
            info(PREMIUM_SLOT, MenuIcon.PREMIUM.item().name(messages.get("shop.premium"))
                    .lore(messages.get("shop.premium-owned")).state(MenuState.OWNED).build());
        } else if (state == State.ACTIVATING) {
            info(PREMIUM_SLOT, MenuIcon.PREMIUM.item().name(messages.get("shop.premium"))
                    .lore(messages.get("shop.premium-activating")).state(MenuState.PENDING).build());
        } else {
            set(PREMIUM_SLOT, premiumIcon());
        }
    }

    private ItemStack balanceIcon() {
        var messages = menus.messages();
        return MenuIcon.TOOL_SHOP.item().name(messages.get("shop.balance-title", Formatter.number("coins", balance)))
                .lore(messages.getLines("shop.balance-lore")).build();
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
        switch (slot) {
            case HEADS_SLOT -> menus.openHeads(player);
            case HATS_SLOT -> menus.openHats(player);
            case SHIRTS_SLOT -> menus.openShirts(player);
            case KILL_EFFECTS_SLOT -> menus.openKillEffects(player, menus::openShop);
            case WIN_EFFECTS_SLOT -> menus.openWinEffects(player, menus::openShop);
            case PREMIUM_SLOT -> buyPremium(player);
            case LEVEL_SLOT -> BlockcraftApi.get().profiles().progression(player, Optional.of(menus::openShop));
        }
    }

    private void buyPremium(Player player) {
        if (state == State.RETRY) {
            activate(player);
            return;
        }
        Purchases.confirm(player, premiumIcon(), Premium.ITEM, menus.config().premiumPrice(), this::activate, this::reopen);
    }

    private void reopen(Player player) {
        var api = BlockcraftApi.get();
        api.playerTasks().latest(player, () -> api.coins().get(player.getUniqueId()), coins -> {
            balance = coins;
            refresh();
            open(player);
        });
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
                .state(state == State.RETRY ? MenuState.PENDING : MenuState.AVAILABLE)
                .name(messages.get("shop.premium"))
                .lore(messages.get("shop.premium-lore"),
                        state == State.RETRY ? messages.get("shop.premium-retry")
                                : messages.get("shop.price-lore", Formatter.number("coins", menus.config().premiumPrice())))
                .build();
    }
}
