package me.blockcraft.lobby.config;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lobby's {@code game-npcs} section: how near a player comes before a game's figure turns to them, how often one of
 * the figures moves on its own, and whose skin each game's figure wears, by player name; a game without a name
 * wears the default look.
 */
public record GameNpcConfig(double lookRadius, int idleSeconds, Map<LobbyLocation, String> skins) {

    public static GameNpcConfig fromYaml(Map<String, Object> config) {
        var skins = config.get("skins") instanceof Map<?, ?> map ? map : Map.of();
        return new GameNpcConfig(
                Math.clamp(config.get("look-radius") instanceof Number number ? number.doubleValue() : 8.0, 1.0, 32.0),
                Math.clamp(config.get("idle-seconds") instanceof Number number ? number.intValue() : 6, 1, 600),
                Arrays.stream(LobbyLocation.values()).filter(area -> area.game().isPresent())
                        .collect(Collectors.toUnmodifiableMap(Function.identity(),
                                area -> Optional.ofNullable(skins.get(area.key())).map(String::valueOf).orElse(""))));
    }

    public Optional<String> skin(LobbyLocation area) {
        return Optional.ofNullable(skins.get(area)).filter(name -> !name.isBlank());
    }
}
