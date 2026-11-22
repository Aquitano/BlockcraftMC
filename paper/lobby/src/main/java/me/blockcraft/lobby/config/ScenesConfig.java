package me.blockcraft.lobby.config;

import me.blockcraft.common.config.StrictConfig;

import java.util.List;

/**
 * Lobby's {@code scenes} section. In winter {@code snowfight-figures} figures stand on each side of the snowball fight,
 * wearing the skin textures listed in {@code snowfight-skins} in turn; one of them throws every {@code throw-ticks},
 * and one a player hits throws back after {@code answer-ticks}. At Halloween {@code bats} bats circle
 * {@code bat-radius} blocks round their spot once every {@code bat-lap-seconds}, and the ghost drifts
 * {@code ghost-speed} blocks a tick along up to {@code ghost-spots} spots and back to the first.
 */
public record ScenesConfig(int snowfightFigures, List<String> snowfightSkins, int throwTicks, int answerTicks, int bats,
                           double batRadius, double batLapSeconds, int ghostSpots, double ghostSpeed) {

    public static ScenesConfig from(StrictConfig config) {
        var skins = config.texts("snowfight-skins").stream().filter(texture -> !texture.isBlank()).toList();
        return new ScenesConfig(Math.clamp(config.integer("snowfight-figures"), 1, 8), skins,
                Math.clamp(config.integer("throw-ticks"), 5, 400), Math.clamp(config.integer("answer-ticks"), 1, 100),
                Math.clamp(config.integer("bats"), 0, 20), Math.clamp(config.decimal("bat-radius"), 1, 16),
                Math.clamp(config.decimal("bat-lap-seconds"), 2, 60), Math.clamp(config.integer("ghost-spots"), 0, 12),
                Math.clamp(config.decimal("ghost-speed"), 0.02, 1));
    }
}
