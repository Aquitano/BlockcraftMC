package me.blockcraft.system.task;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;

import java.util.List;

/** Rotates through the network's tips, its ranks and channels, and secret hints. */
public final class AutoBroadcast implements Runnable {

    private static final List<String> KEYS = List.of("friends", "premium", "pond", "report", "teamspeak", "retro",
            "support", "youtuber", "secrets", "discord");

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
