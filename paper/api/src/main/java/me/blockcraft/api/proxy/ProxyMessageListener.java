package me.blockcraft.api.proxy;

import me.blockcraft.api.dialog.WelcomeDialog;
import me.blockcraft.api.ignore.Ignores;
import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.player.NewcomerEvent;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.spectate.SpectateRequestEvent;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.time.Duration;

/** Hands what the proxy says about a player to the part of BlockcraftAPI that keeps it. */
public final class ProxyMessageListener implements PluginMessageListener {

    private static final long WELCOME_AFTER_ARRIVAL_TICKS = 60L;

    private final Plugin plugin;
    private final ResourcePacks resourcePacks;
    private final Ignores ignores;
    private final PlayerSettings settings;

    public ProxyMessageListener(Plugin plugin, ResourcePacks resourcePacks, Ignores ignores, PlayerSettings settings) {
        this.plugin = plugin;
        this.resourcePacks = resourcePacks;
        this.ignores = ignores;
        this.settings = settings;
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
                case ProxyMessage.Setting setting -> settings.changedOnProxy(player, setting.setting(), setting.value());
                case ProxyMessage.Welcome welcome -> {
                    var rules = GsonComponentSerializer.gson().deserialize(welcome.rulesJson());
                    Tasks.later(plugin, WELCOME_AFTER_ARRIVAL_TICKS, () -> {
                        if (player.isOnline()) {
                            WelcomeDialog.open(player, rules);
                        }
                    });
                }
                case ProxyMessage.Newcomer newcomer ->
                        new NewcomerEvent(newcomer.player(), newcomer.name(), Duration.ofSeconds(newcomer.seconds())).callEvent();
                // The lobby's FriendsChannel waits for these.
                case ProxyMessage.FriendsReply _ -> {
                }
            }
        }, () -> plugin.getLogger().warning("Dropped an unreadable message on " + channel + " for " + player.getName()));
    }
}
