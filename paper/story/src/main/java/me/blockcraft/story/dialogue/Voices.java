package me.blockcraft.story.dialogue;

import net.kyori.adventure.key.Key;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * The recorded voice of every spoken line, by the line's key: {@code vela.greeting} for an NPC option of the
 * conversation {@code vela}, {@code cutscene.intro.1} for a cutscene's line. {@code resourcepack/StoryVoices.py} writes
 * {@code voices.properties} with each recording's length in ticks, so a subtitle can keep pace with it.
 */
public final class Voices {

    public record Voice(Key sound, int ticks) { }

    private final Map<String, Integer> lengths;

    private Voices(Map<String, Integer> lengths) {
        this.lengths = lengths;
    }

    public static Voices load() {
        var properties = new Properties();
        try (var input = Voices.class.getResourceAsStream("/voices.properties")) {
            if (input != null) properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read voices.properties", failure);
        }
        return new Voices(properties.stringPropertyNames().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key, key -> Integer.parseInt(properties.getProperty(key).trim()))));
    }

    public Optional<Voice> of(String line) {
        return Optional.ofNullable(lengths.get(line)).map(ticks -> new Voice(sound(line), ticks));
    }

    /** The sound event a line's recording plays as, the same name the pack's sounds.json gives it. */
    public static Key sound(String line) {
        return Key.key("blockcraft", "story.voice." + line.toLowerCase(Locale.ROOT));
    }
}
