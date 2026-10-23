package me.blockcraft.system.network;

import com.google.gson.JsonObject;
import me.blockcraft.system.SystemConfig;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** Posts one kind of alert to the Discord webhook, at most once per {@code alerts.min-interval-seconds}. */
final class AlertWebhook {

    private final HttpClient http = HttpClient.newHttpClient();
    private final SystemConfig.Alerts alerts;
    private final Logger logger;
    private Instant lastAlert;

    AlertWebhook(SystemConfig.Alerts alerts, Logger logger, Instant lastAlert) {
        this.alerts = alerts;
        this.logger = logger;
        this.lastAlert = lastAlert;
    }

    /** False when nothing was sent because the webhook is disabled or the interval has not passed yet. */
    boolean send(String content) {
        if (alerts.webhookUrl().isBlank()) {
            return false;
        }
        var now = Instant.now();
        if (Duration.between(lastAlert, now).toSeconds() < alerts.minIntervalSeconds()) {
            return false;
        }
        lastAlert = now;
        post(alerts.webhookUrl(), content);
        return true;
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
                logger.warn("Could not send the alert to the webhook", failure);
            } else if (response.statusCode() >= 300) {
                logger.warn("The webhook answered the alert with status {}", response.statusCode());
            }
        });
    }
}
