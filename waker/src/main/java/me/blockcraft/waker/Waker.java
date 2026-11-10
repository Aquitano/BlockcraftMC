package me.blockcraft.waker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Starts and stops game servers for the proxy, which decides when (README, "Server discovery and groups"). It is the
 * only service with the Docker socket, which is root on the host; so it publishes no port, wants a token and touches
 * nothing but the compose services in {@code WAKER_SERVICES}.
 */
public final class Waker {

    /** {@code WAKER_TOKEN}, {@code WAKER_SERVICES} (comma separated), {@code WAKER_PROJECT} and the optional rest. */
    record Settings(String token, Set<String> services, String project, int port, Path socket, int stopSeconds) {

        static Optional<Settings> fromEnv(Map<String, String> env) {
            String token = env.getOrDefault("WAKER_TOKEN", "").trim();
            var services = Arrays.stream(env.getOrDefault("WAKER_SERVICES", "").split(","))
                    .map(String::trim).filter(service -> !service.isEmpty()).collect(Collectors.toUnmodifiableSet());
            String project = env.getOrDefault("WAKER_PROJECT", "").trim();
            if (token.isEmpty() || services.isEmpty() || project.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Settings(token, services, project,
                    Integer.parseInt(env.getOrDefault("WAKER_PORT", "8080")),
                    Path.of(env.getOrDefault("DOCKER_SOCKET", "/var/run/docker.sock")),
                    Integer.parseInt(env.getOrDefault("WAKER_STOP_SECONDS", "60"))));
        }
    }

    private Waker() {
    }

    public static void main(String[] args) throws IOException {
        var settings = Settings.fromEnv(System.getenv());
        if (settings.isEmpty()) {
            System.err.println("Set WAKER_TOKEN, WAKER_SERVICES and WAKER_PROJECT");
            System.exit(1);
            return;
        }
        var config = settings.get();
        var requests = new Requests(config.token(), config.services(),
                new Docker(config.socket(), config.project(), config.stopSeconds()));
        var server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/", exchange -> answer(exchange, requests));
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Waker for " + config.project() + " listens on " + config.port() + ": " + config.services());
    }

    private static void answer(HttpExchange exchange, Requests requests) throws IOException {
        try (exchange) {
            var authorization = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Authorization"));
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            var reply = requests.handle(method, path, authorization);
            if (reply.status() != 401 && method.equals("POST")) {
                System.out.println(method + " " + path + " -> " + reply.status());
            }
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            if (body.length > 0) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
            }
            exchange.sendResponseHeaders(reply.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
        }
    }
}
