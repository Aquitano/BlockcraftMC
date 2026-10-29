package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.MenuTile;
import me.blockcraft.api.pack.MenuState;
import me.blockcraft.lobby.LobbyPermissions;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Illustrated cosmetic categories, with the lobby selector and cookie game below them. */
final class ExtrasMenu extends Menu {

    private static final int GADGETS_SLOT = 9;
    private static final int KILL_EFFECTS_SLOT = 12;
    private static final int WIN_EFFECTS_SLOT = 15;
    private static final int TRAILS_SLOT = 27;
    private static final int PREMIUM_SLOT = 30;
    private static final int TAGS_SLOT = 33;
    private static final int LOBBIES_SLOT = 45;
    private static final int COOKIES_SLOT = 51;

    private final Menus menus;

    ExtrasMenu(Menus menus, Player viewer) {
        super(6, menus.messages().get("extras.title"));
        this.menus = menus;
        var messages = menus.messages();
        closeButton(ToolbarButton.LEFT);
        toolbar(ToolbarButton.MIDDLE, MenuIcon.TOOL_PROFILE.mark(Items.head(viewer.getUniqueId(), BlockcraftApi.get().realName(viewer)))
                .name(messages.get("extras.profile")).lore(messages.get("extras.profile-lore")).build(), menus::openProfile);
        toolbar(ToolbarButton.RIGHT, MenuIcon.TOOL_SHOP.item()
                .name(messages.get("extras.shop")).lore(messages.get("extras.shop-lore")).build(), menus::openShop);
        tile(GADGETS_SLOT, MenuTile.GADGETS, MenuTile.Shape.LARGE, MenuIcon.GADGETS.item().name(messages.get("extras.gadgets")).lore(messages.get("extras.gadgets-lore")).build());
        tile(KILL_EFFECTS_SLOT, MenuTile.KILL_EFFECTS, MenuTile.Shape.LARGE, MenuIcon.KILL_EFFECTS.item().name(messages.get("extras.kill-effects")).lore(messages.get("extras.kill-effects-lore")).build());
        tile(WIN_EFFECTS_SLOT, MenuTile.WIN_EFFECTS, MenuTile.Shape.LARGE, MenuIcon.WIN_EFFECTS.item().name(messages.get("extras.win-effects")).lore(messages.get("extras.win-effects-lore")).build());
        tile(TRAILS_SLOT, MenuTile.TRAILS, MenuTile.Shape.LARGE, MenuIcon.TRAILS.item().name(messages.get("premium-menu.trails")).lore(messages.get("premium-menu.trails-lore")).build());
        tile(PREMIUM_SLOT, MenuTile.PREMIUM, MenuTile.Shape.LARGE, MenuIcon.PREMIUM.item()
                .name(messages.get("extras.premium")).lore(messages.get("extras.premium-lore"))
                .state(viewer.hasPermission(LobbyPermissions.PREMIUM) ? MenuState.OWNED : MenuState.LOCKED).build());
        tile(TAGS_SLOT, MenuTile.TAGS, MenuTile.Shape.LARGE, MenuIcon.CHAT_TAGS.item().name(messages.get("extras.tags")).lore(messages.get("extras.tags-lore")).build());
        tile(LOBBIES_SLOT, MenuTile.LOBBIES, MenuTile.Shape.WIDE, MenuIcon.LOBBIES.item()
                .name(messages.get("extras.lobbies")).lore(messages.get("extras.lobbies-lore")).build());
        set(COOKIES_SLOT, Items.of(Material.COOKIE).name(messages.get("extras.cookies"))
                .lore(messages.get("extras.cookies-lore")).build());
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        switch (slot) {
            case GADGETS_SLOT -> menus.openGadgets(player);
            case KILL_EFFECTS_SLOT -> menus.openKillEffects(player);
            case WIN_EFFECTS_SLOT -> menus.openWinEffects(player);
            case TRAILS_SLOT -> menus.openTrails(player);
            case PREMIUM_SLOT -> {
                if (player.hasPermission(LobbyPermissions.PREMIUM)) {
                    menus.openPremium(player);
                } else {
                    close(player);
                    menus.messages().getLines("premium.required").forEach(player::sendMessage);
                }
            }
            case TAGS_SLOT -> menus.openChatTags(player);
            case LOBBIES_SLOT -> menus.openLobbies(player);
            case COOKIES_SLOT -> menus.openCookies(player);
            default -> close(player);
        }
    }
}
