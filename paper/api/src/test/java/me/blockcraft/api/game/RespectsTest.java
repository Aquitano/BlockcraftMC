package me.blockcraft.api.game;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RespectsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void onlyALoneFCountsAsRespect() {
        assertTrue(Respects.isRespect("F"));
        assertTrue(Respects.isRespect("  f "));
        assertFalse(Respects.isRespect("ff"));
        assertFalse(Respects.isRespect("F für Kevos"));
    }

    @Test
    public void everyPayerCountsOnceAndTheDeceasedNever() throws Exception {
        var messages = Messages.load(folder.newFolder().toPath(), () -> new ByteArrayInputStream("""
                respect:
                  one: '<player> zollt <deceased> Respekt.'
                  many: '<count> Spieler zollen <deceased> Respekt.'
                """.getBytes(StandardCharsets.UTF_8)));
        var kevos = UUID.randomUUID();
        var tribute = new Respects.Tribute(kevos, "Kevos");

        assertEquals(Optional.empty(), tribute.line(messages));
        tribute.pay(kevos, "Kevos");
        tribute.pay(UUID.randomUUID(), "Lisa");
        assertEquals("Lisa zollt Kevos Respekt.", plain(tribute.line(messages)));

        var steve = UUID.randomUUID();
        tribute.pay(steve, "Steve");
        tribute.pay(steve, "Steve");
        tribute.pay(UUID.randomUUID(), "Alex");
        tribute.pay(UUID.randomUUID(), "Notch");
        assertEquals("4 Spieler zollen Kevos Respekt.", plain(tribute.line(messages)));
    }

    private static String plain(Optional<Component> line) {
        return PlainTextComponentSerializer.plainText().serialize(line.orElseThrow());
    }
}
