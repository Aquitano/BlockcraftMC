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

final class NavigatorMenu extends Menu {

    private static final Map<Integer, LobbyLocation> GAMES = Map.of(
            11, LobbyLocation.QSG, 13, LobbyLocation.RUSH, 15, LobbyLocation.DUELL);
    private static final Map<Integer, LobbyLocation> PLACES = Map.of(
            29, LobbyLocation.SPAWN, 31, LobbyLocation.SHOP, 33, LobbyLocation.JUMP);

    private final Menus menus;

    NavigatorMenu(Menus menus) {
        super(5, menus.messages().get("navigator.title"));
        this.menus = menus;
        game(11, LobbyLocation.QSG, Material.IRON_SWORD);
        game(13, LobbyLocation.RUSH, Material.EMERALD);
        game(15, LobbyLocation.DUELL, Material.SHIELD);
        place(29, "spawn", Material.ENDER_EYE);
        place(31, "shop", Material.GOLD_INGOT);
        place(33, "jump", Material.DIAMOND_BOOTS);
        closeButton(44);
    }

    @Override
    protected boolean fillEmptySlots() {
        return false;
    }

    private void game(int slot, LobbyLocation target, Material material) {
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
        set(slot, Items.of(material).name(messages.get("navigator." + target.key())).lore(lore).hideAttributes().build());
    }

    private void place(int slot, String key, Material material) {
        set(slot, Items.of(material).name(menus.messages().get("navigator." + key))
                .lore(menus.messages().get("navigator.lore")).hideAttributes().build());
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
        var place = PLACES.get(slot);
        if (place != null) {
            menus.warp(player, place);
        }
    }
}
