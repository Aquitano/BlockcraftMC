package me.blockcraft.bedwars.config;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.bedwars.resource.Resource;
import me.blockcraft.bedwars.team.TeamColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record BedWarsConfig(
        List<TeamColor> teams,
        int playersPerTeam,
        int lobbySeconds,
        int voteClosesAt,
        List<Integer> voteSlots,
        int roundSeconds,
        int restartSeconds,
        int idleRestartMinutes,
        Map<Resource, Integer> spawnTicks,
        double voidHeight,
        int webCooldownSeconds,
        int webLifetimeSeconds,
        int mobileShopSeconds,
        int parachuteSeconds,
        int teleporterSeconds,
        int teleporterHeight,
        double rescuePlatformRadius,
        int killCoins,
        int winCoins,
        String globalStatsHead,
        CombatStyle combat
) {

    private static final int MIN_TO_START = 2;

    public static BedWarsConfig from(FileConfiguration config) {
        var teams = config.getStringList("teams.colors").stream()
                .map(key -> TeamColor.byKey(key).orElseThrow(() -> new IllegalArgumentException("Unknown team colour " + key)))
                .distinct()
                .toList();
        var spawnTicks = new EnumMap<Resource, Integer>(Resource.class);
        for (var resource : Resource.values()) {
            spawnTicks.put(resource, Math.max(1, config.getInt("resources." + resource.key() + "-ticks", 20)));
        }
        var slots = config.getIntegerList("voting.slots");
        return new BedWarsConfig(
                teams.isEmpty() ? List.of(TeamColor.RED, TeamColor.GREEN, TeamColor.PURPLE, TeamColor.YELLOW) : teams,
                Math.max(1, config.getInt("teams.players-per-team", 2)),
                config.getInt("countdown.lobby", 60),
                config.getInt("countdown.vote-closes-at", 10),
                slots.isEmpty() ? List.of(1, 4, 7) : slots,
                config.getInt("countdown.round", 3600),
                config.getInt("countdown.restart", 30),
                config.getInt("idle-restart-minutes", 50),
                spawnTicks,
                config.getDouble("void-height", -50.0),
                config.getInt("webs.cooldown-seconds", 20),
                config.getInt("webs.lifetime-seconds", 10),
                config.getInt("items.mobile-shop-seconds", 20),
                config.getInt("items.parachute-seconds", 30),
                config.getInt("items.teleporter-seconds", 5),
                config.getInt("items.teleporter-height", 150),
                config.getDouble("items.rescue-platform-radius", 2.5),
                config.getInt("rewards.kill", 5),
                config.getInt("rewards.win", 20),
                config.getString("global-stats-head", "Kevos"),
                CombatStyle.from(config));
    }

    public int capacity() {
        return teams.size() * playersPerTeam;
    }

    /** Half of all places, as in the original; a forced start makes do with two. */
    public int minPlayers() {
        return Math.max(MIN_TO_START, capacity() / 2);
    }

    public int minToStart() {
        return MIN_TO_START;
    }

    public int spawnTicks(Resource resource) {
        return Optional.ofNullable(spawnTicks.get(resource)).orElse(20);
    }
}
