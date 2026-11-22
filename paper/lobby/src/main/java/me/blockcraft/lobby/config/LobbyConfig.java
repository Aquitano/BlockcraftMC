package me.blockcraft.lobby.config;

import me.blockcraft.api.game.Stat;
import me.blockcraft.common.config.StrictConfig;
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
        long friendsTimeoutTicks,
        Duration talkCooldown
) {

    public static LobbyConfig from(StrictConfig root) {
        var prices = root.section("prices");
        var secrets = root.section("secrets");
        var doubleJump = root.section("double-jump");
        var jumpPad = root.section("jump-pad");
        var navigator = root.section("navigator");
        var effects = root.section("effects");
        var leaderboards = root.section("leaderboards");
        Map<String, Leaderboard> boards = leaderboards.section("holograms").values().entrySet().stream()
                .flatMap(entry -> leaderboard(entry.getValue())
                        .map(board -> Map.entry(String.valueOf(entry.getKey()), board)).stream())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<LobbyLocation, String> navigatorGroups = navigator.section("groups").values().entrySet().stream()
                .flatMap(entry -> LobbyLocation.byKey(String.valueOf(entry.getKey())).stream()
                        .map(location -> Map.entry(location, String.valueOf(entry.getValue()))))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        List<Vector> fireworkSpots = root.values().get("fireworks") instanceof List<?> spots
                ? spots.stream().filter(Map.class::isInstance).map(spot -> {
                    var map = (Map<?, ?>) spot;
                    return new Vector(number(map, "x", 0), number(map, "y", 0), number(map, "z", 0));
                }).toList()
                : List.of();
        return new LobbyConfig(
                root.integer("max-players"),
                prices.integer("premium"),
                prices.integer("head"),
                prices.integer("shirt"),
                prices.integer("hat"),
                secrets.integer("reward"),
                Math.max(0, secrets.integer("riddles-after")),
                root.integer("shop-npc.respawn-seconds"),
                fireworkSpots,
                doubleJump.decimal("height"),
                doubleJump.decimal("multiply"),
                jumpPad.decimal("y"),
                jumpPad.decimal("multiply"),
                jumpPad.decimal("max-length"),
                Math.clamp(root.integer("player-hider.cooldown-millis"), 0L, Integer.MAX_VALUE * 50L),
                navigatorGroups,
                root.texts("holograms"),
                boards,
                Math.max(1, leaderboards.integer("refresh-seconds")),
                navigator.integer("stale-seconds"),
                effects.integer("kill-price"),
                effects.integer("win-price"),
                Math.clamp(root.integer("gadgets.cooldown-seconds"), 0, Integer.MAX_VALUE / 20),
                Math.max(1, effects.integer("trail-interval-ticks")),
                Math.clamp(effects.integer("trail-view-distance"), 1, 128),
                Math.clamp(effects.integer("preview-ticks"), 20, 200),
                Math.max(1, root.integer("hud.refresh-seconds")),
                Duration.ofMinutes(root.integer("recap.max-age-minutes")),
                Math.max(20L, root.integer("friends.timeout-ticks")),
                Duration.ofMillis(Math.max(0L, root.integer("talk.cooldown-millis")))
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
}
