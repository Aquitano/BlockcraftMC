package me.blockcraft.endergames.config;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.LootTable;
import me.blockcraft.api.kit.KitCatalog;
import me.blockcraft.endergames.kit.Kit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/** EnderGames' own keys of its config.yml; the lobby's are {@link me.blockcraft.api.game.GameConfig}'s. */
public record EnderGamesConfig(int maxPlayers, Countdowns countdowns, WorldSettings world, StartIslandSettings startIsland,
                               EnderChestSettings enderChests,
                               SwapSettings swap, Rewards rewards, CombatStyle combat, CompassTracker.Settings tracker,
                               KitCatalog<Kit> kits, Abilities abilities) {

    public record Countdowns(int full, int freeze, int protection, int round, int trackerAfter) {
    }

    public record WorldSettings(int radius, List<MapSeed> seeds, int shrinkAfter, int finalRadius) {
    }

    public record StartIslandSettings(int chests, LootTable loot, LootTable enderChestLoot) {
    }

    public record EnderChestSettings(double perPlayer, int min, int max, int relocateSeconds, int afterOpenSeconds,
                                     LootTable loot) {

        public int countFor(int players) {
            return Math.clamp((int) Math.ceil(players * perPlayer), min, Math.max(min, max));
        }
    }

    public record SwapSettings(int minSeconds, int maxSeconds) {
    }

    public record Rewards(int kill, int win) {
    }

    /** Health is in half hearts, durations in seconds. */
    public record Abilities(double headshotMultiplier, int lumberjackMaxLogs, int pearlsPerKill, double vampireMobHeal,
                            double kangarooMinHealth, int cobwebsPerKill, double stomperMaxFallDamage,
                            double stomperRadius, int scorpionMeleeSeconds, int scorpionArrowSeconds,
                            int thorCooldownSeconds, int thorRange, int teleporterInvulnerableSeconds,
                            int berserkerSeconds, int scaredySeconds, double soupHeal) {
    }

    public static EnderGamesConfig from(ConfigurationSection yaml) {
        var config = BundledDefaults.strict(yaml);
        var countdown = config.section("countdown");
        var world = config.section("world");
        var startIsland = config.section("start-island");
        var chests = config.section("enderchests");
        var swap = config.section("swap");
        var rewards = config.section("rewards");
        var abilities = config.section("abilities");
        var startIslandYaml = section(yaml, "start-island");
        int swapMin = Math.max(10, swap.integer("min-seconds"));
        return new EnderGamesConfig(
                Math.max(2, config.integer("players.max")),
                new Countdowns(
                        Math.max(1, countdown.integer("full")),
                        Math.max(0, countdown.integer("freeze")),
                        Math.max(0, countdown.integer("protection")),
                        Math.max(60, countdown.integer("round")),
                        Math.max(1, countdown.integer("tracker-after"))),
                new WorldSettings(
                        Math.max(32, world.integer("radius")),
                        seeds(section(yaml, "world")),
                        Math.max(1, world.integer("shrink-after")),
                        Math.max(5, world.integer("final-radius"))),
                new StartIslandSettings(Math.clamp(startIsland.integer("chests"), 1, 8), LootTable.fromYaml(startIslandYaml),
                        LootTable.fromYaml(section(startIslandYaml, "ender-chests"))),
                new EnderChestSettings(
                        Math.max(0, chests.decimal("per-player")),
                        Math.max(0, chests.integer("min")),
                        Math.max(0, chests.integer("max")),
                        Math.max(10, chests.integer("relocate-seconds")),
                        Math.max(1, chests.integer("after-open-seconds")),
                        LootTable.fromYaml(section(yaml, "enderchests"))),
                new SwapSettings(swapMin, Math.max(swapMin, swap.integer("max-seconds"))),
                new Rewards(Math.max(0, rewards.integer("kill")), Math.max(0, rewards.integer("win"))),
                CombatStyle.from(yaml),
                CompassTracker.Settings.from(yaml),
                KitCatalog.from(section(yaml, "kits"), Kit.class, Kit::defaultPrice),
                new Abilities(
                        Math.max(1, abilities.decimal("headshot-multiplier")),
                        Math.max(1, abilities.integer("lumberjack-max-logs")),
                        Math.max(0, abilities.integer("enderman-pearls-per-kill")),
                        Math.max(0, abilities.decimal("vampire-mob-heal")),
                        Math.max(0, abilities.decimal("kangaroo-min-health")),
                        Math.max(0, abilities.integer("spider-cobwebs-per-kill")),
                        Math.max(0, abilities.decimal("stomper-max-fall-damage")),
                        Math.max(0.5, abilities.decimal("stomper-radius")),
                        Math.max(0, abilities.integer("scorpion-melee-seconds")),
                        Math.max(0, abilities.integer("scorpion-arrow-seconds")),
                        Math.max(0, abilities.integer("thor-cooldown-seconds")),
                        Math.max(1, abilities.integer("thor-range")),
                        Math.max(0, abilities.integer("teleporter-invulnerable-seconds")),
                        Math.max(0, abilities.integer("berserker-seconds")),
                        Math.max(0, abilities.integer("scaredy-seconds")),
                        Math.max(0, abilities.decimal("soup-heal"))));
    }

    private static ConfigurationSection section(ConfigurationSection config, String key) {
        var section = config.getConfigurationSection(key);
        return section != null ? section : config.createSection(key);
    }

    private static List<MapSeed> seeds(ConfigurationSection world) {
        var seeds = world.getMapList("seeds").stream()
                .map(entry -> new MapSeed(number(entry.get("seed")), (int) number(entry.get("x")), (int) number(entry.get("z"))))
                .toList();
        if (seeds.isEmpty()) {
            throw new IllegalStateException("EnderGames needs at least one entry under world.seeds in config.yml");
        }
        return seeds;
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
