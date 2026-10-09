package me.blockcraft.api.proxy;

import me.blockcraft.api.ignore.Ignores;
import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.spectate.SpectateRequestEvent;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.logging.Logger;

/** Hands what the proxy says about a player to the part of BlockcraftAPI that keeps it. */
public final class ProxyMessageListener implements PluginMessageListener {

    private final Logger logger;
    private final ResourcePacks resourcePacks;
    private final Ignores ignores;

    public ProxyMessageListener(Logger logger, ResourcePacks resourcePacks, Ignores ignores) {
        this.logger = logger;
        this.resourcePacks = resourcePacks;
        this.ignores = ignores;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(ProxyMessageCodec.CHANNEL)) {
            return;
        }
        ProxyMessageCodec.decode(message, ProxyMessage.ToBackend.class).ifPresentOrElse(received -> {
            switch (received) {
                case ProxyMessage.Pack pack -> resourcePacks.update(player, pack.status());
                case ProxyMessage.Spectate spectate -> new SpectateRequestEvent(player, spectate.friend()).callEvent();
                case ProxyMessage.Ignore ignore -> ignores.update(player, ignore.other(), ignore.ignoring());
                // The lobby's FriendsChannel waits for these.
                case ProxyMessage.FriendsReply _ -> {
                }
            }
        }, () -> logger.warning("Dropped an unreadable message on " + channel + " for " + player.getName()));
    }
}
