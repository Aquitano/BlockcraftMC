package me.blockcraft.waker;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DockerTest {

    @Test
    public void readsAChunkedResponse() {
        String raw = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n"
                + "5\r\n[{\"a\"\r\n4\r\n:1}]\r\n0\r\n\r\n";

        var response = Docker.Response.parse(raw.getBytes(StandardCharsets.ISO_8859_1));

        assertEquals(200, response.status());
        assertEquals("[{\"a\":1}]", response.body());
    }

    @Test
    public void readsAStatusWithoutBody() {
        assertEquals(304, Docker.Response.parse("HTTP/1.1 304 Not Modified\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1)).status());
    }

    @Test
    public void findsTheContainerOfAFilteredList() {
        String json = "[{\"Id\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\",\"Names\":[\"/blockcraftmc-bedwars-1\"],"
                + "\"Labels\":{\"com.docker.compose.service\":\"bedwars\"},\"State\":\"exited\",\"Status\":\"Exited (0) 3 minutes ago\"}]";

        var container = Docker.find(json).orElseThrow();

        assertTrue(container.id().startsWith("0123456789abcdef"));
        assertEquals("exited", container.state());
        assertTrue(Docker.find("[]").isEmpty());
    }
}
