package me.blockcraft.api.pack;

import me.blockcraft.common.sound.FeedbackSound;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FeedbackSoundTest {

    @Test
    public void customSoundsHaveDefinitionsAndVorbisAssets() throws IOException {
        var pack = Path.of("../../resourcepack/src/assets/blockcraft");
        var definitions = Files.readString(pack.resolve("sounds.json"));
        for (var feedback : FeedbackSound.values()) {
            var sound = feedback.sound(true).name();
            if (!sound.namespace().equals("blockcraft")) {
                continue;
            }
            assertTrue(sound.value(), definitions.contains("\"" + sound.value() + "\""));
            var path = sound.value().replace('.', '/');
            assertTrue(path, definitions.contains("blockcraft:" + path));
            byte[] bytes = Files.readAllBytes(pack.resolve("sounds/" + path + ".ogg"));
            assertEquals("OggS", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
            assertTrue(path + " Vorbis", new String(bytes, StandardCharsets.ISO_8859_1).contains("vorbis"));
        }
    }
}
