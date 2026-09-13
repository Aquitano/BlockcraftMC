package me.blockcraft.lobby.menu;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.lobby.friends.FriendEntry;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The open requests on the top row, the friends below them. Every click runs a {@code /friend} command on the
 * proxy, which owns the friendships and knows where everybody is.
 */
final class FriendsMenu extends Menu {

    private static final int REQUEST_SLOTS = 9;
    private static final int FIRST_FRIEND_SLOT = 9;
    private static final int FRIEND_SLOTS = 16;
    private static final int NOTE_SLOT = 25;
    private static final int CLOSE_SLOT = 26;

    private final Menus menus;
    private final List<FriendEntry> friends;
    private final List<String> requests;

    FriendsMenu(Menus menus, List<FriendEntry> friends, List<String> requests) {
        super(3, menus.messages().get("friends.title"));
        this.menus = menus;
        this.friends = friends.subList(0, Math.min(friends.size(), FRIEND_SLOTS));
        this.requests = requests.subList(0, Math.min(requests.size(), REQUEST_SLOTS));
        var messages = menus.messages();
        for (int index = 0; index < this.requests.size(); index++) {
            String name = this.requests.get(index);
            set(index, Items.head(name)
                    .name(messages.get("friends.request", Placeholder.unparsed("player", name)))
                    .lore(messages.getLines("friends.request-lore"))
                    .build());
        }
        for (int index = 0; index < this.friends.size(); index++) {
            var friend = this.friends.get(index);
            set(FIRST_FRIEND_SLOT + index, Items.head(friend.name())
                    .name(messages.get(friend.online() ? "friends.online" : "friends.offline",
                            Placeholder.unparsed("player", friend.name())))
                    .lore(messages.getLines(friend.online() ? "friends.online-lore" : "friends.offline-lore",
                            Placeholder.unparsed("server", friend.server())))
                    .build());
        }
        if (this.friends.isEmpty()) {
            set(FIRST_FRIEND_SLOT, Items.of(Material.BARRIER).name(messages.get("friends.empty"))
                    .lore(messages.get("friends.empty-lore")).build());
        }
        set(NOTE_SLOT, Items.of(Material.WRITABLE_BOOK).name(messages.get("friends.note"))
                .lore(messages.getLines("friends.note-lore")).build());
        closeButton(CLOSE_SLOT);
    }

    @Override
    protected void onClick(Player player, int slot, ItemStack clicked, InventoryClickEvent event) {
        if (slot < requests.size()) {
            close(player);
            run(player, event.isRightClick() ? "friend deny " : "friend accept ", requests.get(slot));
            return;
        }
        int friend = slot - FIRST_FRIEND_SLOT;
        if (friend >= 0 && friend < friends.size()) {
            close(player);
            run(player, "friend jump ", friends.get(friend).name());
        }
    }

    private void run(Player player, String command, String name) {
        ProxyMessaging.runOnProxy(menus.plugin(), player, command + name);
    }
}
