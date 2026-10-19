package me.blockcraft.rush.config;

import me.blockcraft.api.game.CombatStyle;
import org.bukkit.configuration.file.FileConfiguration;

/** EmeraldRush's own keys; the ones every game shares are {@link me.blockcraft.api.game.GameConfig}'s. */
public record RushConfig(
        int maxPlayers,
        int emeraldsPerOre,
        int oreRespawnSeconds,
        int searchSeconds,
        int shoppingSeconds,
        int deathmatchSeconds,
        int trackerAfterSeconds,
        double trackerRangeHorizontal,
        double trackerRangeVertical,
        int trackerCooldownTicks,
        int lives,
        int killCoins,
        int winCoins,
        int dagobertEmeralds,
        CombatStyle combat
) {

    public static RushConfig from(FileConfiguration config) {
        return new RushConfig(
                config.getInt("players.max", 6),
                config.getInt("emeralds-per-ore", 12),
                config.getInt("ore-respawn-seconds", 12),
                config.getInt("countdown.search", 300),
                config.getInt("countdown.shopping", 90),
                config.getInt("countdown.deathmatch", 1200),
                config.getInt("countdown.deathmatch-tracker-after", 300),
                config.getDouble("tracker.range-horizontal", 100.0),
                config.getDouble("tracker.range-vertical", 25.0),
                Math.max(0, config.getInt("tracker.cooldown-ticks", 20)),
                config.getInt("lives", 3),
                config.getInt("rewards.kill", 5),
                config.getInt("rewards.win", 15),
                config.getInt("moments.dagobert-emeralds", 100),
                CombatStyle.from(config));
    }
}
