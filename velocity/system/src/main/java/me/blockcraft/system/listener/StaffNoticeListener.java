package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent.ForwardResult;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.network.StaffNoticeChannel;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/** Delivers a backend's staff notice to every player on the network who holds the requested permission. */
public final class StaffNoticeListener {

    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(StaffNoticeChannel.NAME);

    private final ProxyServer proxy;

    public StaffNoticeListener(ProxyServer proxy) {
        this.proxy = proxy;
        proxy.getChannelRegistrar().register(CHANNEL);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }
        var in = event.dataAsDataStream();
        String permission = in.readUTF();
        var notice = GsonComponentSerializer.gson().deserialize(in.readUTF());
        for (Player player : proxy.getAllPlayers()) {
            if (player.hasPermission(permission)) {
                player.sendMessage(notice);
            }
        }
    }
}
