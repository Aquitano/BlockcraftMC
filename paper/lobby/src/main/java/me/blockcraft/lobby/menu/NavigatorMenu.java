package me.blockcraft.lobby.menu;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.network.GroupStatus;
import me.blockcraft.lobby.config.LobbyLocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/** The toolbar closes, opens the shop or warps to spawn; the games sit above JumpAndRun. */
final class NavigatorMenu extends Menu {

    private static final Map<Integer, LobbyLocation> GAMES = Map.of(
            18, LobbyLocation.QSG, 20, LobbyLocation.RUSH, 22, LobbyLocation.DUELL, 24, LobbyLocation.BEDWARS,
            26, LobbyLocation.TTT, 28, LobbyLocation.MLGRUSH, 30, LobbyLocation.CORES, 32, LobbyLocation.ENDERGAMES,
            34, LobbyLocation.ZOMBIES);
    private static final int JUMP_SLOT = 40;

    private final Menus menus;

    NavigatorMenu(Menus menus) {
        super(6, menus.messages().get("navigator.title"));
        this.menus = menus;
        closeButton(ToolbarButton.LEFT);
        toolbar(ToolbarButton.MIDDLE, place(MenuIcon.TOOL_SHOP, "shop", "navigator.shop-lore"), menus::openShop);
        toolbar(ToolbarButton.RIGHT, place(MenuIcon.TOOL_HOME, "spawn", "navigator.spawn-lore"),
                player -> menus.warp(player, LobbyLocation.SPAWN));
        GAMES.forEach((slot, target) -> set(slot, game(target)));
        set(JUMP_SLOT, place(MenuIcon.JUMP, "jump", "navigator.lore"));
    }

    @Override
    protected boolean fillEmptySlots() {
        return false;
    }

    private static MenuIcon icon(LobbyLocation game) {
        return switch (game) {
            case QSG -> MenuIcon.QUICKSG;
            case RUSH -> MenuIcon.EMERALDRUSH;
            case BEDWARS -> MenuIcon.BEDWARS;
            case MLGRUSH -> MenuIcon.MLGRUSH;
            case ZOMBIES -> MenuIcon.ZOMBIES;
            case CORES -> MenuIcon.CORES;
            case ENDERGAMES -> MenuIcon.ENDERGAMES;
            case TTT -> MenuIcon.TTT;
            default -> MenuIcon.DUELL;
        };
    }

    private ItemStack game(LobbyLocation target) {
        var messages = menus.messages();
        String group = menus.config().navigatorGroups().get(target);
        var status = group == null ? GroupStatus.OFFLINE : menus.groups().status(group);
        var lore = new ArrayList<>(messages.getLines("navigator.description." + target.key()));
        lore.add(Component.empty());
        lore.add(menus.apiMessages().get("game-status." + status.availability().name().toLowerCase(Locale.ROOT)));
        lore.add(messages.get("navigator.online", Formatter.number("online", status.online())));
        lore.add(Component.empty());
        lore.add(messages.get(status.joinable() ? "navigator.play" : "navigator.retry"));
        if (menus.locations().get(target).isPresent()) {
            lore.add(messages.get("navigator.visit"));
        }
        return icon(target).item().name(messages.get("navigator." + target.key())).lore(lore).build();
    }

    private ItemStack place(MenuIcon icon, String key, String loreKey) {
        return icon.item().name(menus.messages().get("navigator." + key)).lore(menus.messages().get(loreKey)).build();
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (!event.isLeftClick() && !event.isRightClick()) {
            return;
        }
        var game = GAMES.get(slot);
        if (game != null) {
            if (event.isRightClick()) {
                menus.warp(player, game);
            } else {
                String group = menus.config().navigatorGroups().get(game);
                if (group == null || group.isBlank()) {
                    player.sendMessage(menus.messages().get("navigator.unavailable"));
                    return;
                }
                // The proxy checks availability at the moment of the click, just as it does for /play and signs.
                close(player);
                ProxyMessaging.play(menus.plugin(), player, group);
            }
            return;
        }
        if (slot == JUMP_SLOT) {
            menus.warp(player, LobbyLocation.JUMP);
        }
    }
}
