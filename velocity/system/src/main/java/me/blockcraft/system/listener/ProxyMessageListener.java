package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent.ForwardResult;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.network.ProxyChannel;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerVisibility;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.slf4j.Logger;

import java.util.Optional;

/** Answers the backends' requests on the Blockcraft channel; the player the message travelled with is the subject. */
public final class ProxyMessageListener {

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyChannel.NAME);

    private final ProxyServer proxy;
    private final LobbyConnector lobby;
    private final PlayerVisibility visibility;
    private final PlayerChoices choices;
    private final TabHeaderListener tabHeader;
    private final Logger logger;

    public ProxyMessageListener(ProxyServer proxy, LobbyConnector lobby, PlayerVisibility visibility, PlayerChoices choices,
                                TabHeaderListener tabHeader, Logger logger) {
        this.proxy = proxy;
        this.lobby = lobby;
        this.visibility = visibility;
        this.choices = choices;
        this.tabHeader = tabHeader;
        this.logger = logger;
        proxy.getChannelRegistrar().register(CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }
        var player = connection.getPlayer();
        var in = event.dataAsDataStream();
        String subchannel = in.readUTF();
        switch (subchannel) {
            case ProxyChannel.PLAY -> lobby.play(player, in.readUTF());
            case ProxyChannel.HUB -> lobby.send(player);
            case ProxyChannel.COMMAND -> proxy.getCommandManager().executeAsync(player, in.readUTF());
            case ProxyChannel.NICK -> visibility.setNick(player, Optional.of(in.readUTF()).filter(nick -> !nick.isEmpty()));
            case ProxyChannel.SETTING -> {
                String key = in.readUTF();
                String value = in.readUTF();
                PlayerSetting.byKey(key).ifPresent(setting -> choices.set(player, setting, value));
            }
            case ProxyChannel.VANISH -> {
                visibility.setVanished(player, in.readBoolean());
                tabHeader.refreshAll();
            }
            case ProxyChannel.STAFF_NOTICE -> {
                String permission = in.readUTF();
                var notice = GsonComponentSerializer.gson().deserialize(in.readUTF());
                for (Player online : proxy.getAllPlayers()) {
                    if (online.hasPermission(permission)) {
                        online.sendMessage(notice);
                    }
                }
            }
            default -> logger.warn("Unknown subchannel {} from {}", subchannel, connection.getServerInfo().getName());
        }
    }
}
