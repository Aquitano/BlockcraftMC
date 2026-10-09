package me.blockcraft.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.common.message.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Enforces the network-wide mute the proxy toggles with /globalmute. */
public final class GlobalMuteListener implements Listener {

    private final NetworkChat network;
    private final Messages messages;

    public GlobalMuteListener(NetworkChat network, Messages messages) {
        this.network = network;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (event.getPlayer().hasPermission(ChatPermissions.GLOBAL_MUTE_BYPASS)) {
            return;
        }
        if (network.globalMute()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("globalmute.blocked"));
        }
    }
}
