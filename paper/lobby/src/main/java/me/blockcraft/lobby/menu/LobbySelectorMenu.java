package me.blockcraft.lobby.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.network.ServerRecord;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.List;

final class LobbySelectorMenu extends Menu {

    private static final int PAGE_SIZE = 45;
    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_SLOT = 48;
    private static final int NEXT_SLOT = 50;
    private static final int REFRESH_SLOT = 53;

    private final Menus menus;
    private List<ServerRecord> lobbies;
    private int page;
    private boolean loading;
    private boolean failed;

    LobbySelectorMenu(Menus menus, List<ServerRecord> lobbies) {
        super(6, menus.messages().get("lobbies.title"));
        this.menus = menus;
        this.lobbies = List.copyOf(lobbies);
        backButton(BACK_SLOT, menus::openExtras);
        render();
    }

    private void render() {
        var messages = menus.messages();
        int pages = Math.max(1, (lobbies.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (slot == 22 && lobbies.isEmpty()) {
                info(slot, Items.of(Material.BARRIER).name(messages.get("lobbies.empty")).build());
            } else if (index >= lobbies.size()) {
                set(slot, null);
            } else {
                var lobby = lobbies.get(index);
                boolean current = lobby.name().equals(menus.self().name());
                var icon = Items.of(current ? Material.EMERALD_BLOCK : Material.EMERALD)
                        .name(messages.get("lobbies.entry", Placeholder.unparsed("server", lobby.name())))
                        .lore(messages.get(current ? "lobbies.current-lore" : "lobbies.join-lore",
                                Formatter.number("online", lobby.status().online()),
                                Formatter.number("max", lobby.status().maxPlayers())));
                if (current) {
                    info(slot, icon.glow().build());
                } else {
                    set(slot, icon.build());
                }
            }
        }
        set(PREVIOUS_SLOT, page > 0 ? Items.of(Material.ARROW).name(messages.get("lobbies.previous")).build() : null);
        set(NEXT_SLOT, page + 1 < pages ? Items.of(Material.ARROW).name(messages.get("lobbies.next")).build() : null);
        info(49, Items.of(Material.PAPER).name(messages.get("lobbies.page", Formatter.number("page", page + 1),
                Formatter.number("pages", pages))).build());
        if (loading) {
            info(REFRESH_SLOT, Items.of(Material.CLOCK).name(menus.apiMessages().get("menu.load.pending")).build());
        } else {
            set(REFRESH_SLOT, Items.of(failed ? Material.REDSTONE : Material.CLOCK)
                    .name(failed ? menus.apiMessages().get("menu.load.retry") : messages.get("lobbies.refresh")).build());
        }
    }

    private void refresh(Player player) {
        loading = true;
        failed = false;
        render();
        BlockcraftApi.get().playerTasks().latest(player,
                () -> menus.servers().alive(menus.config().serverListStaleSeconds()).stream()
                        .filter(server -> server.group().equals(menus.self().group()))
                        .sorted(Comparator.comparing(ServerRecord::name)).toList(),
                updated -> {
                    lobbies = List.copyOf(updated);
                    loading = false;
                    render();
                }, ignored -> { }, ignored -> {
                    loading = false;
                    failed = true;
                    Sounds.MENU_DENY.play(player);
                    render();
                });
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        return slot == BACK_SLOT || !loading && (slot == REFRESH_SLOT || !failed);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot == PREVIOUS_SLOT && page > 0) {
            page--;
            render();
        } else if (slot == NEXT_SLOT && (page + 1) * PAGE_SIZE < lobbies.size()) {
            page++;
            render();
        } else if (slot == REFRESH_SLOT) {
            refresh(player);
        } else if (slot < PAGE_SIZE && page * PAGE_SIZE + slot < lobbies.size()) {
            var lobby = lobbies.get(page * PAGE_SIZE + slot);
            close(player);
            BlockcraftApi.get().notifications().feedback(player,
                    menus.messages().get("lobbies.connecting", Placeholder.unparsed("server", lobby.name())));
            ProxyMessaging.connect(menus.plugin(), player, lobby.name());
        }
    }
}
