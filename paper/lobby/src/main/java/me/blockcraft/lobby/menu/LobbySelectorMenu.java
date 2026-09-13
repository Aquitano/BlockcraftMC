package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.network.ServerRecord;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

final class LobbySelectorMenu extends Menu {

    private static final int SLOTS_PER_ROW = 9;

    private final Menus menus;
    private final List<ServerRecord> lobbies;

    LobbySelectorMenu(Menus menus, List<ServerRecord> lobbies) {
        super(rows(lobbies.size()), menus.messages().get("lobbies.title"));
        this.menus = menus;
        this.lobbies = lobbies;
        var messages = menus.messages();
        for (int slot = 0; slot < lobbies.size(); slot++) {
            var lobby = lobbies.get(slot);
            boolean current = lobby.name().equals(menus.self().name());
            var icon = Items.of(current ? Material.EMERALD_BLOCK : Material.EMERALD)
                    .name(messages.get("lobbies.entry", Placeholder.unparsed("server", lobby.name())))
                    .lore(messages.get(current ? "lobbies.current-lore" : "lobbies.join-lore",
                            Formatter.number("online", lobby.status().online()),
                            Formatter.number("max", lobby.status().maxPlayers())));
            if (current) {
                icon.glow();
            }
            set(slot, icon.build());
        }
        backButton((rows(lobbies.size()) - 1) * SLOTS_PER_ROW, menus::openExtras);
    }

    private static int rows(int lobbies) {
        return Math.min(6, (lobbies + SLOTS_PER_ROW - 1) / SLOTS_PER_ROW + 1);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        close(player);
        if (slot >= lobbies.size()) {
            return;
        }
        var lobby = lobbies.get(slot);
        if (lobby.name().equals(menus.self().name())) {
            player.sendMessage(menus.messages().get("lobbies.already"));
            return;
        }
        player.sendMessage(menus.messages().get("lobbies.connecting", Placeholder.unparsed("server", lobby.name())));
        ProxyMessaging.connect(menus.plugin(), player, lobby.name());
    }
}
