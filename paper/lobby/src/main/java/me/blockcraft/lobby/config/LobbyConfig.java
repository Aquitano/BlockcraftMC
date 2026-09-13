package me.blockcraft.lobby.config;

import me.blockcraft.common.config.YamlFile;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record LobbyConfig(
        int maxPlayers,
        Set<String> allowedCommands,
        int premiumPrice,
        int headPrice,
        int shirtPrice,
        int secretReward,
        long shopNpcRespawnSeconds,
        List<Vector> fireworkSpots,
        double doubleJumpHeight,
        double doubleJumpMultiply,
        double jumpPadY,
        double jumpPadMultiply,
        double jumpPadMaxLength,
        long hiderCooldownMillis,
        Map<LobbyLocation, String> navigatorGroups,
        int serverListStaleSeconds,
        int killEffectPrice,
        int winEffectPrice,
        int gadgetCooldownSeconds
) {

    public static LobbyConfig fromYaml(Map<String, Object> root) {
        var prices = YamlFile.section(root, "prices");
        var secrets = YamlFile.section(root, "secrets");
        var shopNpc = YamlFile.section(root, "shop-npc");
        var doubleJump = YamlFile.section(root, "double-jump");
        var jumpPad = YamlFile.section(root, "jump-pad");
        var hider = YamlFile.section(root, "player-hider");
        var navigator = YamlFile.section(root, "navigator");
        var effects = YamlFile.section(root, "effects");
        var gadgets = YamlFile.section(root, "gadgets");
        Map<LobbyLocation, String> navigatorGroups = YamlFile.section(navigator, "groups").entrySet().stream()
                .flatMap(entry -> LobbyLocation.byKey(String.valueOf(entry.getKey())).stream()
                        .map(location -> Map.entry(location, String.valueOf(entry.getValue()))))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        Set<String> allowedCommands = root.get("allowed-commands") instanceof List<?> commands
                ? commands.stream().map(String::valueOf).map(String::toLowerCase).collect(Collectors.toUnmodifiableSet())
                : Set.of();
        List<Vector> fireworkSpots = root.get("fireworks") instanceof List<?> spots
                ? spots.stream().filter(Map.class::isInstance).map(spot -> {
                    var map = (Map<?, ?>) spot;
                    return new Vector(number(map, "x", 0), number(map, "y", 0), number(map, "z", 0));
                }).toList()
                : List.of();
        return new LobbyConfig(
                (int) number(root, "max-players", 100),
                allowedCommands,
                (int) number(prices, "premium", 15000),
                (int) number(prices, "head", 300),
                (int) number(prices, "shirt", 500),
                (int) number(secrets, "reward", 50),
                (long) number(shopNpc, "respawn-seconds", 500),
                fireworkSpots,
                number(doubleJump, "height", 0.6),
                number(doubleJump, "multiply", 1.0),
                number(jumpPad, "y", 0.18),
                number(jumpPad, "multiply", 5.0),
                number(jumpPad, "max-length", 6.0),
                (long) number(hider, "cooldown-millis", 3000),
                navigatorGroups,
                (int) number(navigator, "stale-seconds", 20),
                (int) number(effects, "kill-price", 500),
                (int) number(effects, "win-price", 500),
                (int) number(gadgets, "cooldown-seconds", 5)
        );
    }

    private static double number(Map<?, ?> section, String key, double fallback) {
        return section.get(key) instanceof Number value ? value.doubleValue() : fallback;
    }
}
