package me.blockcraft.system.task;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;

/** Rotates through the network hints (TeamSpeak, YouTuber, Discord, shop, report, support) on every tick. */
public final class AutoBroadcast implements Runnable {

    private static final List<String> KEYS = List.of("teamspeak", "youtuber", "discord", "premium", "report", "support");

    private final ProxyServer proxy;
    private final SystemConfig config;
    private final Messages messages;
    private int next;

    public AutoBroadcast(ProxyServer proxy, SystemConfig config, Messages messages) {
        this.proxy = proxy;
        this.config = config;
        this.messages = messages;
    }

    @Override
    public void run() {
        String key = KEYS.get(next);
        next = (next + 1) % KEYS.size();
        var lines = messages.getLines("broadcast." + key, TagResolver.resolver(
                Placeholder.unparsed("teamspeak", config.network().teamspeak()),
                Placeholder.unparsed("discord", config.network().discord()),
                Placeholder.unparsed("shop", config.network().shop())));
        for (Player player : proxy.getAllPlayers()) {
            lines.forEach(player::sendMessage);
        }
    }
}
