package me.blockcraft.lobby.config;

import me.blockcraft.common.config.StrictConfig;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lobby's {@code game-npcs} section: how near a player comes before a game's figure turns to them and before a
 * sleeping game starts its server, how often one of the figures moves on its own, and whose skin each game's figure
 * wears, by player name; a game without a name wears the game host's skin.
 */
public record GameNpcConfig(double lookRadius, double wakeRadius, int idleSeconds, Map<LobbyLocation, String> skins) {

    public static GameNpcConfig from(StrictConfig config) {
        var skins = config.section("skins").values();
        return new GameNpcConfig(
                Math.clamp(config.decimal("look-radius"), 1.0, 32.0),
                Math.clamp(config.decimal("wake-radius"), 1.0, 64.0),
                Math.clamp(config.integer("idle-seconds"), 1, 600),
                Arrays.stream(LobbyLocation.values()).filter(area -> area.game().isPresent())
                        .collect(Collectors.toUnmodifiableMap(Function.identity(),
                                area -> Optional.ofNullable(skins.get(area.key())).map(String::valueOf).orElse(""))));
    }

    public Optional<String> skin(LobbyLocation area) {
        return Optional.ofNullable(skins.get(area)).filter(name -> !name.isBlank());
    }
}
