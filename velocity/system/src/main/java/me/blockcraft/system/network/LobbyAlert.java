package me.blockcraft.system.network;

import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import org.slf4j.Logger;

import java.time.Instant;

/**
 * Tells a Discord webhook when the lobby group has no joinable server left and when one is back. A change the
 * rate limit swallows stays unannounced and is picked up again by the next discovery poll.
 */
public final class LobbyAlert {

    private final ProxyServer proxy;
    private final ServerDirectory directory;
    private final SystemConfig config;
    private final Messages messages;
    private final AlertWebhook webhook;
    private boolean announcedAvailable = true;

    public LobbyAlert(ProxyServer proxy, ServerDirectory directory, SystemConfig config, Messages messages, Logger logger) {
        this.proxy = proxy;
        this.directory = directory;
        this.config = config;
        this.messages = messages;
        // The proxy is up before its backends are, so the first interval stays quiet; a lobby still missing then is announced.
        this.webhook = new AlertWebhook(config.alerts(), logger, Instant.now());
    }

    void check() {
        boolean available = directory.balance(config.lobbyGroup()).isPresent();
        if (available != announcedAvailable && webhook.send(text(available))) {
            announcedAvailable = available;
        }
    }

    private String text(boolean available) {
        return messages.raw(available ? "alerts.lobby-back" : "alerts.no-lobby")
                .replace("<ip>", config.network().ip())
                .replace("<online>", String.valueOf(proxy.getPlayerCount()));
    }
}
