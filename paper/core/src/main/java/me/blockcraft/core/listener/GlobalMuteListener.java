package me.blockcraft.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** Enforces the network-wide mute the proxy toggles with /globalmute. */
public final class GlobalMuteListener implements Listener {

    private final Plugin plugin;
    private final NetworkChat network;
    private final Messages messages;

    public GlobalMuteListener(Plugin plugin, NetworkChat network, Messages messages) {
        this.plugin = plugin;
        this.network = network;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (event.getPlayer().hasPermission(ChatPermissions.GLOBAL_MUTE_BYPASS)) {
            return;
        }
        if (network.globalMute()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("globalmute.blocked"));
            Tasks.sync(plugin, () -> Sounds.MENU_DENY.play(event.getPlayer()));
        }
    }
}
