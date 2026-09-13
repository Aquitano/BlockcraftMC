package me.blockcraft.system.network;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Tells a Discord webhook when the lobby group has no joinable server left and when one is back. A change the
 * rate limit swallows stays unannounced and is picked up again by the next discovery poll.
 */
public final class LobbyAlert {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ProxyServer proxy;
    private final ServerDirectory directory;
    private final SystemConfig config;
    private final Messages messages;
    private final Logger logger;
    private boolean announcedAvailable = true;
    /** The proxy is up before its backends are, so the first interval stays quiet; a lobby still missing then is announced. */
    private Instant lastAlert = Instant.now();

    public LobbyAlert(ProxyServer proxy, ServerDirectory directory, SystemConfig config, Messages messages, Logger logger) {
        this.proxy = proxy;
        this.directory = directory;
        this.config = config;
        this.messages = messages;
        this.logger = logger;
    }

    void check() {
        var alerts = config.alerts();
        boolean available = directory.balance(config.lobbyGroup()).isPresent();
        if (available == announcedAvailable || alerts.webhookUrl().isBlank()) {
            return;
        }
        var now = Instant.now();
        if (Duration.between(lastAlert, now).toSeconds() < alerts.minIntervalSeconds()) {
            return;
        }
        lastAlert = now;
        announcedAvailable = available;
        post(alerts.webhookUrl(), text(available));
    }

    private String text(boolean available) {
        return messages.raw(available ? "alerts.lobby-back" : "alerts.no-lobby")
                .replace("<ip>", config.network().ip())
                .replace("<online>", String.valueOf(proxy.getPlayerCount()));
    }

    private void post(String webhookUrl, String content) {
        var body = new JsonObject();
        body.addProperty("content", content);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            logger.warn("alerts.webhook-url is not a usable URL, no alert was sent", e);
            return;
        }
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, failure) -> {
            if (failure != null) {
                logger.warn("Could not send the lobby alert to the webhook", failure);
            } else if (response.statusCode() >= 300) {
                logger.warn("The webhook answered the lobby alert with status {}", response.statusCode());
            }
        });
    }
}
