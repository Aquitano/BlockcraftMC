package me.blockcraft.lobby.config;

import me.blockcraft.api.game.Stat;
import me.blockcraft.common.config.YamlFile;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record LobbyConfig(
        int maxPlayers,
        int premiumPrice,
        int headPrice,
        int shirtPrice,
        int hatPrice,
        int secretReward,
        int secretRiddlesAfter,
        long shopNpcRespawnSeconds,
        List<Vector> fireworkSpots,
        double doubleJumpHeight,
        double doubleJumpMultiply,
        double jumpPadY,
        double jumpPadMultiply,
        double jumpPadMaxLength,
        long hiderCooldownMillis,
        Map<LobbyLocation, String> navigatorGroups,
        List<String> holograms,
        Map<String, Leaderboard> leaderboards,
        int leaderboardRefreshSeconds,
        int serverListStaleSeconds,
        int killEffectPrice,
        int winEffectPrice,
        int gadgetCooldownSeconds,
        int trailIntervalTicks,
        int trailViewDistance,
        int cosmeticPreviewTicks,
        int hudRefreshSeconds,
        Duration recapMaxAge,
        long friendsTimeoutTicks
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
        var leaderboards = YamlFile.section(root, "leaderboards");
        Map<String, Leaderboard> boards = YamlFile.section(leaderboards, "holograms").entrySet().stream()
                .flatMap(entry -> leaderboard(entry.getValue())
                        .map(board -> Map.entry(String.valueOf(entry.getKey()), board)).stream())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<LobbyLocation, String> navigatorGroups = YamlFile.section(navigator, "groups").entrySet().stream()
                .flatMap(entry -> LobbyLocation.byKey(String.valueOf(entry.getKey())).stream()
                        .map(location -> Map.entry(location, String.valueOf(entry.getValue()))))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        List<Vector> fireworkSpots = root.get("fireworks") instanceof List<?> spots
                ? spots.stream().filter(Map.class::isInstance).map(spot -> {
                    var map = (Map<?, ?>) spot;
                    return new Vector(number(map, "x", 0), number(map, "y", 0), number(map, "z", 0));
                }).toList()
                : List.of();
        return new LobbyConfig(
                (int) number(root, "max-players", 100),
                (int) number(prices, "premium", 15000),
                (int) number(prices, "head", 300),
                (int) number(prices, "shirt", 500),
                (int) number(prices, "hat", 800),
                (int) number(secrets, "reward", 50),
                Math.max(0, (int) number(secrets, "riddles-after", 3)),
                (long) number(shopNpc, "respawn-seconds", 500),
                fireworkSpots,
                number(doubleJump, "height", 0.6),
                number(doubleJump, "multiply", 1.0),
                number(jumpPad, "y", 0.18),
                number(jumpPad, "multiply", 5.0),
                number(jumpPad, "max-length", 6.0),
                Math.clamp((long) number(hider, "cooldown-millis", 3000), 0L, Integer.MAX_VALUE * 50L),
                navigatorGroups,
                strings(root, "holograms"),
                boards,
                Math.max(1, (int) number(leaderboards, "refresh-seconds", 300)),
                (int) number(navigator, "stale-seconds", 20),
                (int) number(effects, "kill-price", 500),
                (int) number(effects, "win-price", 500),
                Math.clamp((int) number(gadgets, "cooldown-seconds", 5), 0, Integer.MAX_VALUE / 20),
                Math.max(1, (int) number(effects, "trail-interval-ticks", 2)),
                Math.clamp((int) number(effects, "trail-view-distance", 32), 1, 128),
                Math.clamp((int) number(effects, "preview-ticks", 60), 20, 200),
                Math.max(1, (int) number(YamlFile.section(root, "hud"), "refresh-seconds", 5)),
                Duration.ofMinutes((long) number(YamlFile.section(root, "recap"), "max-age-minutes", 10)),
                Math.max(20L, (long) number(YamlFile.section(root, "friends"), "timeout-ticks", 200))
        );
    }

    private static Optional<Leaderboard> leaderboard(Object value) {
        if (!(value instanceof Map<?, ?> board)
                || !(board.get("game") instanceof String game)
                || !(board.get("table") instanceof String table)) {
            return Optional.empty();
        }
        return Stat.parse(String.valueOf(board.get("stat"))).map(stat -> new Leaderboard(game, table, stat));
    }

    private static double number(Map<?, ?> section, String key, double fallback) {
        return section.get(key) instanceof Number value ? value.doubleValue() : fallback;
    }

    private static List<String> strings(Map<String, Object> root, String key) {
        return root.get(key) instanceof List<?> values ? values.stream().map(String::valueOf).toList() : List.of();
    }
}
