package me.blockcraft.skywars.config;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.LootTable;
import me.blockcraft.api.kit.KitCatalog;
import me.blockcraft.skywars.kit.Kit;
import org.bukkit.configuration.ConfigurationSection;


/** SkyWars' own keys of its config.yml; the lobby's are {@link me.blockcraft.api.game.GameConfig}'s. */
public record SkyWarsConfig(int maxPlayers, Countdowns countdowns, Arena arena, Loot loot, Rewards rewards,
                            CombatStyle combat, KitCatalog<Kit> kits) {

    public record Countdowns(int full, int freeze, int round, int refillAfter, int borderAfter) {
    }

    /** Below {@code voidHeight} a player dies; nobody builds above {@code buildHeight}; the border ends at {@code finalRadius}. */
    public record Arena(int voidHeight, int buildHeight, int finalRadius) {
    }

    /** What the chests hold: a spawn island's three by category, the semi-middles' and the middle's. */
    public record Loot(LootTable armour, LootTable weapons, LootTable utility, LootTable semiMiddle, LootTable middle) {
    }

    public record Rewards(int kill, int win) {
    }

    public static SkyWarsConfig from(ConfigurationSection config) {
        var countdown = section(config, "countdown");
        var arena = section(config, "arena");
        var chests = section(config, "chests");
        var rewards = section(config, "rewards");
        int round = Math.max(60, countdown.getInt("round", 900));
        return new SkyWarsConfig(
                Math.clamp(config.getInt("players.max", 12), 2, 12),
                new Countdowns(
                        Math.max(1, countdown.getInt("full", 15)),
                        Math.max(1, countdown.getInt("freeze", 5)),
                        round,
                        Math.clamp(countdown.getInt("refill-after", 300), 1, round),
                        Math.clamp(countdown.getInt("border-after", 600), 1, round)),
                new Arena(arena.getInt("void-height", 40), arena.getInt("build-height", 125),
                        Math.max(4, arena.getInt("final-radius", 12))),
                new Loot(table(chests, "armour"), table(chests, "weapons"), table(chests, "utility"),
                        table(chests, "semi-middle"), table(chests, "middle")),
                new Rewards(Math.max(0, rewards.getInt("kill", 5)), Math.max(0, rewards.getInt("win", 20))),
                CombatStyle.from(config),
                KitCatalog.from(section(config, "kits"), Kit.class, Kit::defaultPrice));
    }

    private static LootTable table(ConfigurationSection chests, String key) {
        return LootTable.fromYaml(section(chests, key), 3, 5);
    }

    private static ConfigurationSection section(ConfigurationSection config, String key) {
        var section = config.getConfigurationSection(key);
        return section != null ? section : config.createSection(key);
    }
}
