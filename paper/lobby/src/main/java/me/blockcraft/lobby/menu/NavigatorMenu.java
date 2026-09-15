package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.network.GroupStatus;
import me.blockcraft.lobby.config.LobbyLocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

/** The toolbar closes, opens the shop or warps to spawn; the games sit in the middle row, JumpAndRun below them. */
final class NavigatorMenu extends Menu {

    private static final Map<Integer, LobbyLocation> GAMES = Map.of(
            20, LobbyLocation.QSG, 22, LobbyLocation.RUSH, 24, LobbyLocation.DUELL);
    private static final int JUMP_SLOT = 40;

    private final Menus menus;

    NavigatorMenu(Menus menus) {
        super(6, menus.messages().get("navigator.title"));
        this.menus = menus;
        var messages = menus.messages();
        var apiMessages = menus.apiMessages();
        toolbar(ToolbarButton.LEFT, Items.of(Material.BARRIER).name(apiMessages.get("menu.close")).build(), "tool_close", Menu::close);
        toolbar(ToolbarButton.MIDDLE, place(Material.GOLD_INGOT, "shop", "navigator.shop-lore"), "tool_shop", menus::openShop);
        toolbar(ToolbarButton.RIGHT, place(Material.ENDER_EYE, "spawn", "navigator.spawn-lore"), "tool_home",
                player -> menus.warp(player, LobbyLocation.SPAWN));
        GAMES.forEach((slot, target) -> set(slot, game(target), look(target).icon()));
        set(JUMP_SLOT, place(Material.DIAMOND_BOOTS, "jump", "navigator.lore"), "jump");
    }

    @Override
    protected boolean fillEmptySlots() {
        return false;
    }

    private record Look(Material material, String icon) {
    }

    private static Look look(LobbyLocation game) {
        return switch (game) {
            case QSG -> new Look(Material.IRON_SWORD, "quicksg");
            case RUSH -> new Look(Material.EMERALD, "emeraldrush");
            default -> new Look(Material.SHIELD, "duell");
        };
    }

    private ItemStack game(LobbyLocation target) {
        var messages = menus.messages();
        String group = menus.config().navigatorGroups().get(target);
        var status = group == null ? GroupStatus.OFFLINE : menus.groupCounts().status(group);
        var lore = new ArrayList<>(messages.getLines("navigator.description." + target.key()));
        lore.add(Component.empty());
        lore.add(menus.apiMessages().get("game-status." + status.availability().name().toLowerCase(Locale.ROOT)));
        lore.add(messages.get("navigator.online", Formatter.number("online", status.online())));
        lore.add(Component.empty());
        lore.add(messages.get(status.joinable() ? "navigator.play" : "navigator.retry"));
        if (menus.locations().get(target).isPresent()) {
            lore.add(messages.get("navigator.visit"));
        }
        return Items.of(look(target).material()).name(messages.get("navigator." + target.key())).lore(lore).hideAttributes().build();
    }

    private ItemStack place(Material material, String key, String loreKey) {
        return Items.of(material).name(menus.messages().get("navigator." + key))
                .lore(menus.messages().get(loreKey)).hideAttributes().build();
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
