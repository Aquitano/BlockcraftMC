package me.blockcraft.lobby.config;

import java.util.Map;

/** Lobby's {@code guide} section. */
public record GuideConfig(String skin, long greetAfterTicks, long packWaitTicks) {

    public static GuideConfig fromYaml(Map<String, Object> config) {
        return new GuideConfig(config.get("skin") instanceof String skin ? skin : "",
                number(config, "greet-after-ticks", 40, 1, 200), number(config, "pack-wait-seconds", 10, 0, 60) * 20L);
    }

    private static int number(Map<String, Object> config, String key, int fallback, int min, int max) {
        return Math.clamp(config.get(key) instanceof Number number ? number.intValue() : fallback, min, max);
    }
}
