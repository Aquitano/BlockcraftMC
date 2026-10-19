package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.ListMenu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.network.ProxyMessage.FriendsReply;
import me.blockcraft.common.network.ProxyMessage.FriendsRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The friends or the open requests the proxy answered with; the toolbar switches between them and sends a request. */
final class FriendsMenu extends ListMenu<FriendsMenu.Row> {

    sealed interface Row {
        record Friend(FriendsReply.Entry entry) implements Row {
        }

        record Request(String name) implements Row {
        }
    }

    private static final int ROWS = 6;
    private static final int TOOLBAR_SLOTS = 9;

    private final Menus menus;
    private List<FriendsReply.Entry> friends = List.of();
    private List<String> requests = List.of();
    private boolean showRequests;
    private boolean ready;

    FriendsMenu(Menus menus) {
        super(ROWS, menus.messages().get("friends.title"));
        this.menus = menus;
        var messages = menus.messages();
        tabs();
        toolbar(ToolbarButton.RIGHT, MenuIcon.TOOL_ADD_FRIEND.item().name(messages.get("friends.note"))
                .lore(messages.getLines("friends.note-lore")).build(), this::addFriend);
        refreshButton();
        closeButton();
        loading();
    }

    @Override
    public void open(Player player) {
        super.open(player, opened -> load(opened, FriendsRequest.Action.LIST, ""));
    }

    private void tabs() {
        toolbar(ToolbarButton.LEFT, tab(MenuIcon.TOOL_FRIENDS, "friends.list-tab", friends.size(), !showRequests),
                player -> select(false));
        toolbar(ToolbarButton.MIDDLE, tab(MenuIcon.TOOL_REQUESTS, "friends.requests-tab", requests.size(), showRequests),
                player -> select(true));
    }

    private ItemStack tab(MenuIcon icon, String key, int count, boolean selected) {
        var tab = icon.item().name(menus.messages().get(key, Formatter.number("count", count)));
        if (selected) {
            tab.glow();
        }
        return tab.build();
    }

    private void select(boolean requests) {
        showRequests = requests;
        tabs();
        showFromStart(tabRows());
    }

    private List<Row> tabRows() {
        return showRequests
                ? requests.stream().<Row>map(Row.Request::new).toList()
                : friends.stream().<Row>map(Row.Friend::new).toList();
    }

    private void load(Player player, FriendsRequest.Action action, String name) {
        ready = false;
        loading();
        menus.friends().request(player, action, name, reply -> {
            switch (reply.status()) {
                case OK -> {
                    friends = reply.friends().stream().sorted(Comparator.comparing(FriendsReply.Entry::online).reversed()
                            .thenComparing(FriendsReply.Entry::name, String.CASE_INSENSITIVE_ORDER)).toList();
                    requests = reply.requests();
                    ready = true;
                    tabs();
                    show(tabRows());
                }
                case FORBIDDEN -> {
                    Sounds.MENU_DENY.play(player);
                    unavailable(menus.messages().get("friends.forbidden"));
                }
                case FAILED -> {
                    Sounds.MENU_DENY.play(player);
                    failed();
                }
            }
        });
    }

    @Override
    protected void reload(Player player) {
        load(player, FriendsRequest.Action.LIST, "");
    }

    @Override
    protected Component empty() {
        return menus.messages().get(showRequests ? "friends.no-requests" : "friends.empty");
    }

    @Override
    protected ItemStack icon(Row row) {
        var messages = menus.messages();
        return switch (row) {
            case Row.Request(String name) -> Items.head(name)
                    .name(messages.get("friends.request", Placeholder.unparsed("player", name)))
                    .lore(messages.getLines("friends.request-lore"))
                    .build();
            case Row.Friend(FriendsReply.Entry friend) -> {
                var lore = new ArrayList<>(messages.getLines(friend.online()
                                ? friend.watching() ? "friends.watching-lore" : "friends.online-lore" : "friends.offline-lore",
                        Placeholder.unparsed("server", friend.server()), Placeholder.unparsed("player", friend.name())));
                lore.add(messages.get(friend.days() == 1 ? "friends.since-one" : "friends.since",
                        Formatter.number("days", friend.days())));
                yield Items.head(friend.name())
                        .name(messages.get(friend.online() ? "friends.online" : "friends.offline",
                                Placeholder.unparsed("player", friend.name())))
                        .lore(lore)
                        .build();
            }
        };
    }

    @Override
    protected boolean selectable(Row row) {
        return !(row instanceof Row.Friend(FriendsReply.Entry friend)) || friend.online();
    }

    @Override
    protected boolean isEnabled(Player player, int slot, InventoryClickEvent event) {
        return ready || slot >= TOOLBAR_SLOTS;
    }

    @Override
    protected void onSelect(Player player, Row row, InventoryClickEvent event) {
        switch (row) {
            case Row.Request(String name) ->
                    load(player, event.isRightClick() ? FriendsRequest.Action.DENY : FriendsRequest.Action.ACCEPT, name);
            case Row.Friend(FriendsReply.Entry friend) -> {
                if (!event.isRightClick()) {
                    close(player);
                }
                ProxyMessaging.runOnProxy(menus.plugin(), player,
                        (event.isRightClick() ? "party invite " : "friend jump ") + friend.name());
            }
        }
    }

    private void addFriend(Player player) {
        new AddFriendDialog(menus, "", (viewer, name) -> open(viewer, opened -> load(opened, FriendsRequest.Action.ADD, name)),
                this::open).open(player);
    }
}
