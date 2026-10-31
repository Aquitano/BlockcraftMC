package me.blockcraft.waker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Docker's API over its Unix socket, spoken by hand: three calls, so no client library and nothing that could reach
 * further into the daemon than finding a compose service's container and starting or stopping it.
 */
final class Docker implements Containers {

    /** A status line, headers and body, the body already freed of chunked transfer encoding. */
    record Response(int status, String body) {

        static Response parse(byte[] raw) {
            String text = new String(raw, StandardCharsets.ISO_8859_1);
            int end = text.indexOf("\r\n\r\n");
            String head = end < 0 ? text : text.substring(0, end);
            String body = end < 0 ? "" : text.substring(end + 4);
            String[] status = head.lines().findFirst().orElse("").split(" ");
            int code = status.length > 1 ? Integer.parseInt(status[1]) : 0;
            boolean chunked = head.lines().anyMatch(line -> line.toLowerCase(Locale.ROOT).startsWith("transfer-encoding:")
                    && line.toLowerCase(Locale.ROOT).contains("chunked"));
            String decoded = chunked ? dechunk(body) : body;
            return new Response(code, new String(decoded.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
        }

        private static String dechunk(String body) {
            var out = new StringBuilder();
            int at = 0;
            while (at < body.length()) {
                int lineEnd = body.indexOf("\r\n", at);
                if (lineEnd < 0) {
                    break;
                }
                int size = Integer.parseInt(body.substring(at, lineEnd).split(";")[0].trim(), 16);
                if (size == 0) {
                    break;
                }
                out.append(body, lineEnd + 2, lineEnd + 2 + size);
                at = lineEnd + 2 + size + 2;
            }
            return out.toString();
        }
    }

    private static final Pattern ID = Pattern.compile("\"Id\"\\s*:\\s*\"([0-9a-f]{12,64})\"");
    private static final Pattern STATE = Pattern.compile("\"State\"\\s*:\\s*\"([a-z]+)\"");

    private final Path socket;
    private final String project;
    private final int stopSeconds;

    Docker(Path socket, String project, int stopSeconds) {
        this.socket = socket;
        this.project = project;
        this.stopSeconds = stopSeconds;
    }

    @Override
    public Optional<String> state(String service) {
        return container(service).map(Container::state);
    }

    @Override
    public Outcome start(String service) {
        return container(service).map(container -> act("/containers/" + container.id() + "/start")).orElse(Outcome.MISSING);
    }

    @Override
    public Outcome stop(String service) {
        return container(service).map(container -> act("/containers/" + container.id() + "/stop?t=" + stopSeconds))
                .orElse(Outcome.MISSING);
    }

    record Container(String id, String state) {
    }

    /** The service's container by compose's labels, stopped ones included, so one stopped on purpose can be started again. */
    private Optional<Container> container(String service) {
        String filters = "{\"label\":[\"com.docker.compose.project=" + project + "\",\"com.docker.compose.service=" + service + "\"]}";
        try {
            var response = request("GET", "/containers/json?all=1&filters=" + URLEncoder.encode(filters, StandardCharsets.UTF_8));
            if (response.status() != 200) {
                return Optional.empty();
            }
            return find(response.body());
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not look up " + service + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /** The first container of a filtered list, which holds one container per compose service. */
    static Optional<Container> find(String json) {
        var id = ID.matcher(json);
        var state = STATE.matcher(json);
        if (!id.find() || !state.find()) {
            return Optional.empty();
        }
        return Optional.of(new Container(id.group(1), state.group(1)));
    }

    private Outcome act(String target) {
        try {
            int status = request("POST", target).status();
            return switch (status) {
                case 204 -> Outcome.DONE;
                case 304 -> Outcome.ALREADY;
                case 404 -> Outcome.MISSING;
                default -> Outcome.FAILED;
            };
        } catch (IOException | RuntimeException e) {
            System.err.println(target + " failed: " + e.getMessage());
            return Outcome.FAILED;
        }
    }

    private Response request(String method, String target) throws IOException {
        try (var channel = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
            String request = method + " " + target + " HTTP/1.1\r\nHost: docker\r\nConnection: close\r\nContent-Length: 0\r\n\r\n";
            channel.write(ByteBuffer.wrap(request.getBytes(StandardCharsets.US_ASCII)));
            var raw = new ByteArrayOutputStream();
            Channels.newInputStream(channel).transferTo(raw);
            return Response.parse(raw.toByteArray());
        }
    }
}
