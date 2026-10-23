package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.network.GroupStatus;
import me.blockcraft.common.network.ServerRecord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

final class LobbySelectorMenu extends ListMenu<ServerRecord> {

    private static final int ROWS = 6;

    private final Menus menus;

    LobbySelectorMenu(Menus menus, List<ServerRecord> lobbies) {
        super(ROWS, menus.messages().get("lobbies.title"));
        this.menus = menus;
        refreshButton();
        backButton(menus::openExtras);
        show(lobbies);
    }

    private boolean current(ServerRecord lobby) {
        return lobby.name().equals(menus.self().name());
    }

    @Override
    protected ItemStack icon(ServerRecord lobby) {
        var messages = menus.messages();
        boolean current = current(lobby);
        boolean joinable = lobby.joinable();
        var status = GroupStatus.of(List.of(lobby)).availability();
        String lore = current ? "lobbies.current-lore" : joinable ? "lobbies.join-lore" : "lobbies.unavailable-lore";
        var icon = Items.of(current ? Material.EMERALD_BLOCK : joinable ? Material.EMERALD : Material.REDSTONE)
                .name(messages.get("lobbies.entry", Placeholder.unparsed("server", lobby.name())))
                .lore(messages.getLines(lore,
                        Formatter.number("online", lobby.online()),
                        Formatter.number("max", lobby.status().maxPlayers()),
                        Placeholder.component("status", menus.apiMessages().get("game-status."
                                + status.name().toLowerCase(Locale.ROOT)))));
        if (current) {
            icon.glow();
        }
        return icon.build();
    }

    @Override
    protected boolean selectable(ServerRecord lobby) {
        return !current(lobby) && lobby.joinable();
    }

    @Override
    protected Component empty() {
        return menus.messages().get("lobbies.empty");
    }

    @Override
    protected void reload(Player player) {
        loading();
        BlockcraftApi.get().playerTasks().latest(player, menus::lobbies, this::show, ignored -> { }, ignored -> {
            Sounds.MENU_DENY.play(player);
            failed();
        });
    }

    @Override
    protected void onSelect(Player player, ServerRecord lobby, InventoryClickEvent event) {
        close(player);
        BlockcraftApi.get().notifications().feedback(player,
                menus.messages().get("lobbies.connecting", Placeholder.unparsed("server", lobby.name())));
        ProxyMessaging.runOnProxy(menus.plugin(), player, "serverjoin " + lobby.name());
    }
}
