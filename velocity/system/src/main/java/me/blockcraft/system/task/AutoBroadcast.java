package me.blockcraft.system.task;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;

import java.util.List;

/** Rotates through the network hints (TeamSpeak, YouTuber, Discord, shop, report, support) on every tick. */
public final class AutoBroadcast implements Runnable {

    private static final List<String> KEYS = List.of("teamspeak", "youtuber", "discord", "premium", "report", "support");

    private final ProxyServer proxy;
    private final Messages messages;
    private int next;

    public AutoBroadcast(ProxyServer proxy, Messages messages) {
        this.proxy = proxy;
        this.messages = messages;
    }

    @Override
    public void run() {
        String key = KEYS.get(next);
        next = (next + 1) % KEYS.size();
        var lines = messages.getLines("broadcast." + key);
        for (Player player : proxy.getAllPlayers()) {
            lines.forEach(player::sendMessage);
        }
    }
}
