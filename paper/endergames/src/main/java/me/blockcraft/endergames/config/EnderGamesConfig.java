package me.blockcraft.endergames.config;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.LootTable;
import me.blockcraft.endergames.kit.Kit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** EnderGames' own keys of its config.yml; the lobby's are {@link me.blockcraft.api.game.GameConfig}'s. */
public record EnderGamesConfig(int maxPlayers, Countdowns countdowns, WorldSettings world, EnderChestSettings enderChests,
                               SwapSettings swap, Rewards rewards, CombatStyle combat, CompassTracker.Settings tracker,
                               Map<Kit, Integer> kitPrices, Abilities abilities) {

    public record Countdowns(int full, int freeze, int protection, int round, int trackerAfter) {
    }

    public record WorldSettings(int radius, int searchAttempts, int searchStep, double spawnRing, int shrinkAfter,
                                int finalRadius) {
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

    public static EnderGamesConfig from(ConfigurationSection config) {
        var countdown = section(config, "countdown");
        var world = section(config, "world");
        var chests = section(config, "enderchests");
        var swap = section(config, "swap");
        var rewards = section(config, "rewards");
        var abilities = section(config, "abilities");
        int swapMin = Math.max(10, swap.getInt("min-seconds", 60));
        return new EnderGamesConfig(
                Math.max(2, config.getInt("players.max", 24)),
                new Countdowns(
                        Math.max(1, countdown.getInt("full", 15)),
                        Math.max(0, countdown.getInt("freeze", 5)),
                        Math.max(0, countdown.getInt("protection", 90)),
                        Math.max(60, countdown.getInt("round", 1800)),
                        Math.max(1, countdown.getInt("tracker-after", 900))),
                new WorldSettings(
                        Math.max(32, world.getInt("radius", 150)),
                        Math.max(1, world.getInt("search-attempts", 8)),
                        Math.max(64, world.getInt("search-step", 640)),
                        Math.clamp(world.getDouble("spawn-ring", 0.85), 0.1, 0.95),
                        Math.max(1, world.getInt("shrink-after", 1200)),
                        Math.max(5, world.getInt("final-radius", 25))),
                new EnderChestSettings(
                        Math.max(0, chests.getDouble("per-player", 0.5)),
                        Math.max(0, chests.getInt("min", 4)),
                        Math.max(0, chests.getInt("max", 12)),
                        Math.max(10, chests.getInt("relocate-seconds", 150)),
                        Math.max(1, chests.getInt("after-open-seconds", 10)),
                        LootTable.fromYaml(chests, 3, 6)),
                new SwapSettings(swapMin, Math.max(swapMin, swap.getInt("max-seconds", 120))),
                new Rewards(Math.max(0, rewards.getInt("kill", 5)), Math.max(0, rewards.getInt("win", 20))),
                CombatStyle.from(config),
                CompassTracker.Settings.from(config),
                prices(section(config, "kits")),
                new Abilities(
                        Math.max(1, abilities.getDouble("headshot-multiplier", 1.5)),
                        Math.max(1, abilities.getInt("lumberjack-max-logs", 96)),
                        Math.max(0, abilities.getInt("enderman-pearls-per-kill", 2)),
                        Math.max(0, abilities.getDouble("vampire-mob-heal", 6)),
                        Math.max(0, abilities.getDouble("kangaroo-min-health", 8)),
                        Math.max(0, abilities.getInt("spider-cobwebs-per-kill", 8)),
                        Math.max(0, abilities.getDouble("stomper-max-fall-damage", 4)),
                        Math.max(0.5, abilities.getDouble("stomper-radius", 2.5)),
                        Math.max(0, abilities.getInt("scorpion-melee-seconds", 6)),
                        Math.max(0, abilities.getInt("scorpion-arrow-seconds", 2)),
                        Math.max(0, abilities.getInt("thor-cooldown-seconds", 5)),
                        Math.max(1, abilities.getInt("thor-range", 40)),
                        Math.max(0, abilities.getInt("teleporter-invulnerable-seconds", 10)),
                        Math.max(0, abilities.getInt("berserker-seconds", 10)),
                        Math.max(0, abilities.getInt("scaredy-seconds", 3)),
                        Math.max(0, abilities.getDouble("soup-heal", 7))));
    }

    /** A kit the file leaves out costs what the kit itself asks by default. */
    static Map<Kit, Integer> prices(ConfigurationSection kits) {
        var prices = new EnumMap<Kit, Integer>(Kit.class);
        Arrays.stream(Kit.values()).forEach(kit -> prices.put(kit, Math.max(0, kits.getInt(kit.key(), kit.defaultPrice()))));
        return prices;
    }

    public int price(Kit kit) {
        return kitPrices.getOrDefault(kit, kit.defaultPrice());
    }

    public List<Kit> freeKits() {
        return Arrays.stream(Kit.values()).filter(kit -> price(kit) == 0).toList();
    }

    private static ConfigurationSection section(ConfigurationSection config, String key) {
        var section = config.getConfigurationSection(key);
        return section != null ? section : config.createSection(key);
    }
}
