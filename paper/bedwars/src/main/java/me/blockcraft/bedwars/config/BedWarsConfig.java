package me.blockcraft.bedwars.config;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.bedwars.resource.Resource;
import me.blockcraft.bedwars.team.TeamColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** BedWars' own keys; the ones every game shares are {@link me.blockcraft.api.game.GameConfig}'s. */
public record BedWarsConfig(
        List<TeamColor> teams,
        int playersPerTeam,
        int voteClosesAt,
        int voteMaps,
        int roundSeconds,
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
        CombatStyle combat
) {

    public static BedWarsConfig from(FileConfiguration yaml) {
        var config = BundledDefaults.strict(yaml);
        var teams = config.texts("teams.colors").stream()
                .map(key -> TeamColor.byKey(key).orElseThrow(() -> new IllegalArgumentException("Unknown team colour " + key)))
                .distinct()
                .toList();
        if (teams.size() < 2) {
            throw new IllegalArgumentException("A round needs at least two teams, teams.colors names only " + teams);
        }
        var spawnTicks = new EnumMap<Resource, Integer>(Resource.class);
        for (var resource : Resource.values()) {
            spawnTicks.put(resource, Math.max(1, config.integer("resources." + resource.key() + "-ticks")));
        }
        return new BedWarsConfig(
                teams,
                Math.max(1, config.integer("teams.players-per-team")),
                config.integer("countdown.vote-closes-at"),
                Math.max(1, config.integer("voting.maps")),
                config.integer("countdown.round"),
                spawnTicks,
                config.decimal("void-height"),
                config.integer("webs.cooldown-seconds"),
                config.integer("webs.lifetime-seconds"),
                config.integer("items.mobile-shop-seconds"),
                config.integer("items.parachute-seconds"),
                config.integer("items.teleporter-seconds"),
                config.integer("items.teleporter-height"),
                config.decimal("items.rescue-platform-radius"),
                config.integer("rewards.kill"),
                config.integer("rewards.win"),
                CombatStyle.from(yaml));
    }

    public int capacity() {
        return teams.size() * playersPerTeam;
    }

    public int spawnTicks(Resource resource) {
        return spawnTicks.get(resource);
    }
}
