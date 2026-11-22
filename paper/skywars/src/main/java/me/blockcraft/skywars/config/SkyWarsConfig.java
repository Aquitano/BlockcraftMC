package me.blockcraft.skywars.config;

import me.blockcraft.api.config.BundledDefaults;
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

    public static SkyWarsConfig from(ConfigurationSection yaml) {
        var config = BundledDefaults.strict(yaml);
        var countdown = config.section("countdown");
        var arena = config.section("arena");
        var chests = section(yaml, "chests");
        var rewards = config.section("rewards");
        int round = Math.max(60, countdown.integer("round"));
        return new SkyWarsConfig(
                Math.clamp(config.integer("players.max"), 2, 12),
                new Countdowns(
                        Math.max(1, countdown.integer("full")),
                        Math.max(1, countdown.integer("freeze")),
                        round,
                        Math.clamp(countdown.integer("refill-after"), 1, round),
                        Math.clamp(countdown.integer("border-after"), 1, round)),
                new Arena(arena.integer("void-height"), arena.integer("build-height"),
                        Math.max(4, arena.integer("final-radius"))),
                new Loot(table(chests, "armour"), table(chests, "weapons"), table(chests, "utility"),
                        table(chests, "semi-middle"), table(chests, "middle")),
                new Rewards(Math.max(0, rewards.integer("kill")), Math.max(0, rewards.integer("win"))),
                CombatStyle.from(yaml),
                KitCatalog.from(section(yaml, "kits"), Kit.class));
    }

    private static LootTable table(ConfigurationSection chests, String key) {
        return LootTable.fromYaml(section(chests, key));
    }

    private static ConfigurationSection section(ConfigurationSection config, String key) {
        var section = config.getConfigurationSection(key);
        return section != null ? section : config.createSection(key);
    }
}
