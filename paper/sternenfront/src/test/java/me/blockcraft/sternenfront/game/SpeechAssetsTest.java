package me.blockcraft.sternenfront.game;

import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import javax.sound.sampled.AudioSystem;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

import static org.junit.Assert.*;

public class SpeechAssetsTest {
    private static final Path PACK = Path.of("../../resourcepack");

    @Test
    public void factionSpeechMatchesItsGermanTextSourcesEventsAndRuntimeTiming() throws Exception {
        var messages = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/messages.yml").toFile());
        var config = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());
        var events = JsonParser.parseString(Files.readString(PACK.resolve("src/assets/blockcraft/sounds.json"))).getAsJsonObject();
        var hashes = new HashSet<String>();
        var texts = new HashSet<String>();
        for (var family : List.of("intro", "bots", "radio")) {
            var manifest = JsonParser.parseString(Files.readString(PACK.resolve("audio/" + family + "/sources.json"))).getAsJsonObject();
            var sources = manifest.getAsJsonObject(family.equals("intro") ? "briefings" : "calls");
            int ticks = config.getInt(switch (family) {
                case "intro" -> "intro.audio.briefing-ticks";
                case "bots" -> "radio.infantry-ticks";
                default -> "radio.call-ticks";
            });
            for (var entry : sources.entrySet()) {
                var source = entry.getValue().getAsJsonObject();
                var path = PACK.resolve("audio/" + family + "/" + entry.getKey() + ".wav");
                var hash = sha256(Files.readAllBytes(path));
                assertEquals(path.toString(), source.get("sha256").getAsString(), hash);
                assertTrue("Each call has its own recording: " + path, hashes.add(hash));
                var key = source.get("message").getAsString();
                var text = messages.getString(key);
                assertNotNull(key, text);
                assertEquals(key, source.get("text_sha256").getAsString(), sha256(text.getBytes(StandardCharsets.UTF_8)));
                assertFalse(key, text.contains("Hegemonie") || text.contains("Kolonien"));
                texts.add(key);
                try (var audio = AudioSystem.getAudioInputStream(path.toFile())) {
                    int actual = (int) Math.ceil(audio.getFrameLength() / (double) audio.getFormat().getFrameRate() * 20);
                    assertEquals(path.toString(), source.get("duration_ticks").getAsInt(), actual);
                    assertTrue(path + " exceeds the runtime speech window", actual <= ticks);
                    assertEquals(1, audio.getFormat().getChannels());
                }
                var event = events.getAsJsonObject(source.get("event").getAsString());
                assertNotNull(key, event);
                var sound = source.get("sound").getAsString();
                assertTrue(key, event.getAsJsonArray("sounds").asList().stream().anyMatch(item ->
                        item.getAsJsonObject().get("name").getAsString().equals("blockcraft:sternenfront/" + sound)));
                assertTrue(key, Files.size(PACK.resolve("src/assets/blockcraft/sounds/sternenfront/" + sound + ".ogg")) > 0);
            }
        }
        for (var faction : Faction.values()) {
            assertTrue(texts.contains("intro.briefing." + faction.key()));
            for (var event : BattleRadio.Event.values()) assertTrue(texts.contains("radio." + faction.key() + "." + event.key));
            for (var cue : List.of("contact", "cover", "advance", "heal")) {
                assertTrue(texts.contains("bot.voice." + faction.key() + "." + cue));
                assertTrue(texts.contains("bot.voice." + faction.key() + "." + cue + "-2"));
                var variants = events.getAsJsonObject("sternenfront.bot." + faction.key() + "." + cue).getAsJsonArray("sounds");
                assertEquals(2, variants.size());
                variants.forEach(variant -> assertEquals(24, variant.getAsJsonObject().get("attenuation_distance").getAsInt()));
            }
        }
        assertEquals(26, texts.size());
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
