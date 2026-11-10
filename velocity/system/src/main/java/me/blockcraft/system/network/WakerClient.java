package me.blockcraft.system.network;

import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * The proxy's line to the waker service, which starts and stops game servers (README, "Server discovery and groups").
 * A group's server is the compose service of the same name. Every answer is true or false; why a call failed goes to
 * the log.
 */
public final class WakerClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration START_TIMEOUT = Duration.ofSeconds(30);
    /** Docker gives a stopping server a minute before it kills it. */
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(90);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    private final URI base;
    private final String token;
    private final Logger logger;

    public WakerClient(URI base, String token, Logger logger) {
        this.base = base;
        this.token = token;
        this.logger = logger;
    }

    public CompletableFuture<Boolean> start(String group) {
        return post(group, "start", START_TIMEOUT);
    }

    public CompletableFuture<Boolean> stop(String group) {
        return post(group, "stop", STOP_TIMEOUT);
    }

    private CompletableFuture<Boolean> post(String group, String action, Duration timeout) {
        var request = HttpRequest.newBuilder(base.resolve("/services/" + group + "/" + action))
                .timeout(timeout)
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).handle((response, error) -> {
            if (error != null) {
                logger.warn("Could not {} {}: {}", action, group, error.toString());
                return false;
            }
            if (response.statusCode() / 100 != 2) {
                logger.warn("The waker answered {} to {} {}", response.statusCode(), action, group);
                return false;
            }
            logger.info("The waker did {} {}", action, group);
            return true;
        });
    }
}
