package me.blockcraft.lobby.listener;

import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

/** Kicks clients that announce the World Downloader mod. */
public final class WorldDownloaderListener implements PluginMessageListener {

    public static final String INIT_CHANNEL = "wdl:init";

    private final Messages messages;

    public WorldDownloaderListener(Messages messages) {
        this.messages = messages;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals(INIT_CHANNEL) && !player.hasPermission(LobbyPermissions.WDL_BYPASS)) {
            player.kick(messages.get("wdl.kick"));
        }
    }
}
