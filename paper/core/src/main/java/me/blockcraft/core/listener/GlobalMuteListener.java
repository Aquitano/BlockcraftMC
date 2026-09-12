package me.blockcraft.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.Setting;
import me.blockcraft.common.moderation.SettingsRepository;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Enforces the network-wide mute the proxy toggles with /globalmute. */
public final class GlobalMuteListener implements Listener {

    private final SettingsRepository settings;
    private final Messages messages;

    public GlobalMuteListener(SettingsRepository settings, Messages messages) {
        this.settings = settings;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (event.getPlayer().hasPermission(ChatPermissions.GLOBAL_MUTE_BYPASS)) {
            return;
        }
        if (settings.isEnabled(Setting.GLOBAL_MUTE)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("globalmute.blocked"));
        }
    }
}
