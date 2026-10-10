package me.blockcraft.api.game;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Player;

/** The spectator hotbar: whom to watch, the players before and after the one in view, and the way out. */
enum SpectatorItem implements HotbarItem {

    PICK_PLAYER(0, MenuIcon.SPECTATE_PLAYERS, "spectator.pick"),
    PREVIOUS_PLAYER(3, MenuIcon.SPECTATE_PREVIOUS, "spectator.previous"),
    NEXT_PLAYER(5, MenuIcon.SPECTATE_NEXT, "spectator.next"),
    LEAVE_ROUND(8, MenuIcon.TOOL_HOME, "spectator.leave");

    private final int slot;
    private final MenuIcon icon;
    private final String messageKey;

    SpectatorItem(int slot, MenuIcon icon, String messageKey) {
        this.slot = slot;
        this.icon = icon;
        this.messageKey = messageKey;
    }

    static void give(Player player, Messages messages) {
        for (var item : values()) {
            player.getInventory().setItem(item.slot, item.build(player, item.icon.item().name(messages.get(item.messageKey))));
        }
    }
}
