package me.blockcraft.lobby.menu;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.network.FriendsMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Comparator;
import java.util.List;

final class FriendsMenu extends Menu {
    private static final int PAGE_SIZE = 36;
    private final Menus menus;
    private List<FriendsMessage.Entry> friends = List.of();
    private List<String> requests = List.of();
    private int page;
    private boolean showRequests;
    private boolean loading = true;
    private FriendsMessage.Status status = FriendsMessage.Status.OK;

    FriendsMenu(Menus menus) {
        super(6, menus.messages().get("friends.title"));
        this.menus = menus;
        closeButton(53);
        render();
    }

    @Override
    public void open(Player player) {
        super.open(player, opened -> load(opened, FriendsMessage.Action.LIST, ""));
    }

    private int size() {
        return showRequests ? requests.size() : friends.size();
    }

    private void render() {
        var messages = menus.messages();
        var list = Items.of(Material.PLAYER_HEAD).name(messages.get("friends.list-tab", Formatter.number("count", friends.size())));
        var inbox = Items.of(Material.PAPER).name(messages.get("friends.requests-tab", Formatter.number("count", requests.size())));
        if (showRequests) {
            set(0, list.build());
            info(1, inbox.glow().build());
        } else {
            info(0, list.glow().build());
            set(1, inbox.build());
        }
        set(4, Items.of(Material.WRITABLE_BOOK).name(messages.get("friends.note")).lore(messages.getLines("friends.note-lore")).build());
        set(7, Items.of(Material.SUNFLOWER).name(messages.get("friends.refresh")).build());
        boolean ready = !loading && status == FriendsMessage.Status.OK;
        int pages = Math.max(1, (size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
        info(49, ready ? Items.of(Material.MAP).name(messages.get("friends.page", Formatter.number("page", page + 1),
                Formatter.number("pages", pages))).build() : null);
        set(45, ready && page > 0 ? Items.of(Material.ARROW).name(messages.get("friends.previous")).build() : null);
        set(51, ready && page + 1 < pages ? Items.of(Material.ARROW).name(messages.get("friends.next")).build() : null);
        for (int slot = 9; slot < 45; slot++) {
            int index = page * PAGE_SIZE + slot - 9;
            if (slot == 22 && (!ready || size() == 0)) {
                String key = loading ? "friends.loading" : switch (status) {
                    case FORBIDDEN -> "friends.forbidden";
                    case FAILED -> "friends.failed";
                    case OK -> showRequests ? "friends.no-requests" : "friends.empty";
                };
                var state = Items.of(loading ? Material.CLOCK : ready ? Material.PAPER : Material.BARRIER)
                        .name(messages.get(key)).build();
                if (!loading && status == FriendsMessage.Status.FAILED) {
                    set(slot, state);
                } else {
                    info(slot, state);
                }
            } else if (!ready || index >= size()) {
                set(slot, null);
            } else if (showRequests) {
                set(slot, Items.head(requests.get(index)).name(messages.get("friends.request", Placeholder.unparsed("player", requests.get(index))))
                        .lore(messages.getLines("friends.request-lore")).build());
            } else {
                var friend = friends.get(index);
                var icon = Items.head(friend.name()).name(messages.get(friend.online() ? "friends.online" : "friends.offline",
                                Placeholder.unparsed("player", friend.name())))
                        .lore(messages.getLines(friend.online() ? friend.watching() ? "friends.watching-lore" : "friends.online-lore" : "friends.offline-lore",
                                Placeholder.unparsed("server", friend.server()))).build();
                if (friend.online()) {
                    set(slot, icon);
                } else {
                    info(slot, icon);
                }
            }
        }
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        if (slot == 53) {
            return true;
        }
        if (loading || status == FriendsMessage.Status.FORBIDDEN) {
            return false;
        }
        if (status == FriendsMessage.Status.FAILED) {
            return slot == 7 || slot == 22;
        }
        return true;
    }

    private void load(Player player, FriendsMessage.Action action, String name) {
        loading = true;
        status = FriendsMessage.Status.OK;
        render();
        menus.friends().request(player, action, name, reply -> {
            loading = false;
            status = reply.status();
            if (status == FriendsMessage.Status.OK) {
                friends = reply.friends().stream().sorted(Comparator.comparing(FriendsMessage.Entry::online).reversed()
                        .thenComparing(FriendsMessage.Entry::name, String.CASE_INSENSITIVE_ORDER)).toList();
                requests = reply.requests();
            } else Sounds.MENU_DENY.play(player);
            render();
        });
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (loading || status == FriendsMessage.Status.FORBIDDEN) {
            return;
        }
        if (slot == 7 || status == FriendsMessage.Status.FAILED && slot == 22) {
            load(player, FriendsMessage.Action.LIST, "");
            return;
        }
        if (status != FriendsMessage.Status.OK) {
            return;
        }
        if (slot == 4) {
            add(player);
            return;
        }
        if (slot == 0 || slot == 1) {
            showRequests = slot == 1;
            page = 0;
            render();
            return;
        }
        if (slot == 45 && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == 51 && (page + 1) * PAGE_SIZE < size()) {
            page++;
            render();
            return;
        }
        int index = page * PAGE_SIZE + slot - 9;
        if (slot < 9 || slot >= 45 || index >= size()) {
            return;
        }
        if (showRequests) {
            load(player, event.isRightClick() ? FriendsMessage.Action.DENY : FriendsMessage.Action.ACCEPT, requests.get(index));
        } else if (friends.get(index).online()) {
            if (!event.isRightClick()) close(player);
            ProxyMessaging.runOnProxy(menus.plugin(), player, (event.isRightClick() ? "party invite " : "friend jump ") + friends.get(index).name());
        } else Sounds.MENU_DENY.play(player);
    }

    private void add(Player player) {
        var messages = menus.messages();
        var dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(messages.get("friends.note")).inputs(List.of(
                        DialogInput.text("player", messages.get("friends.player-name")).maxLength(16).build())).build())
                .type(DialogType.multiAction(List.of(Dialogs.button(messages.get("friends.send"), (viewer, response) -> {
                    String name = response.getText("player");
                    if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) {
                        viewer.sendMessage(messages.get("friends.invalid-name"));
                        add(viewer);
                        return;
                    }
                    super.open(viewer, opened -> load(opened, FriendsMessage.Action.ADD, name));
                }))).exitAction(Dialogs.button(menus.apiMessages().get("menu.back"), previous -> open(previous))).build()));
        Dialogs.show(player, dialog);
    }
}
