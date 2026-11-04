package me.blockcraft.waker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The whole API: {@code GET /services}, {@code POST /services/<name>/start} and {@code POST /services/<name>/stop},
 * each behind the bearer token, and only for the services on the allow list. Everything else is refused.
 */
final class Requests {

    record Reply(int status, String body) {
    }

    private static final Pattern ACTION = Pattern.compile("^/services/([a-z0-9][a-z0-9_-]{0,63})/(start|stop)$");

    private final byte[] expected;
    private final Set<String> services;
    private final Containers containers;

    Requests(String token, Set<String> services, Containers containers) {
        this.expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        this.services = Set.copyOf(services);
        this.containers = containers;
    }

    Reply handle(String method, String path, Optional<String> authorization) {
        if (authorization.filter(this::authorized).isEmpty()) {
            return new Reply(401, "");
        }
        if (path.equals("/services")) {
            return method.equals("GET") ? new Reply(200, list()) : new Reply(405, "");
        }
        var action = ACTION.matcher(path);
        if (!action.matches()) {
            return new Reply(404, "");
        }
        if (!method.equals("POST")) {
            return new Reply(405, "");
        }
        String service = action.group(1);
        if (!services.contains(service)) {
            return new Reply(404, "");
        }
        var outcome = action.group(2).equals("start") ? containers.start(service) : containers.stop(service);
        return switch (outcome) {
            case DONE, ALREADY -> new Reply(204, "");
            case MISSING -> new Reply(404, "");
            case FAILED -> new Reply(502, "");
        };
    }

    /** Constant time, so the token cannot be guessed a byte at a time. */
    private boolean authorized(String header) {
        return MessageDigest.isEqual(expected, header.getBytes(StandardCharsets.UTF_8));
    }

    private String list() {
        return services.stream().sorted()
                .map(service -> "\"" + service + "\":\"" + containers.state(service).orElse("missing") + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }
}
