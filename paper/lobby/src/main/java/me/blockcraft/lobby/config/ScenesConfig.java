package me.blockcraft.lobby.config;

import java.util.List;
import java.util.Map;

/**
 * Lobby's {@code scenes} section. In winter {@code snowfight-figures} figures stand on each side of the snowball fight,
 * wearing the skin textures listed in {@code snowfight-skins} in turn; one of them throws every {@code throw-ticks},
 * and one a player hits throws back after {@code answer-ticks}. At Halloween {@code bats} bats circle
 * {@code bat-radius} blocks round their spot once every {@code bat-lap-seconds}, and the ghost drifts
 * {@code ghost-speed} blocks a tick along up to {@code ghost-spots} spots and back to the first.
 */
public record ScenesConfig(int snowfightFigures, List<String> snowfightSkins, int throwTicks, int answerTicks, int bats,
                           double batRadius, double batLapSeconds, int ghostSpots, double ghostSpeed) {

    public static ScenesConfig fromYaml(Map<String, Object> config) {
        var skins = config.get("snowfight-skins") instanceof List<?> textures
                ? textures.stream().map(String::valueOf).filter(texture -> !texture.isBlank()).toList() : List.<String>of();
        return new ScenesConfig((int) number(config, "snowfight-figures", 3, 1, 8), skins, (int) number(config, "throw-ticks", 25, 5, 400),
                (int) number(config, "answer-ticks", 12, 1, 100), (int) number(config, "bats", 6, 0, 20),
                number(config, "bat-radius", 4, 1, 16), number(config, "bat-lap-seconds", 8, 2, 60),
                (int) number(config, "ghost-spots", 5, 0, 12), number(config, "ghost-speed", 0.12, 0.02, 1));
    }

    private static double number(Map<String, Object> config, String key, double fallback, double min, double max) {
        return Math.clamp(config.get(key) instanceof Number number ? number.doubleValue() : fallback, min, max);
    }
}
