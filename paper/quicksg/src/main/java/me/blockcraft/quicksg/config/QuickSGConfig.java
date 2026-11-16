package me.blockcraft.quicksg.config;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.CompassTracker;
import me.blockcraft.api.game.LootTable;
import me.blockcraft.api.kit.KitCatalog;
import me.blockcraft.quicksg.kit.Kit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;


/** QuickSG's own keys; the ones every game shares are {@link me.blockcraft.api.game.GameConfig}'s. */
public record QuickSGConfig(
        int maxPlayers,
        Countdowns countdowns,
        WorldBorderSettings worldBorder,
        CompassTracker.Settings tracker,
        Rewards rewards,
        KitCatalog<Kit> kits,
        LootTable loot,
        CombatStyle combat
) {

    public record Countdowns(int freeze, int protection, int round, int trackerAfter) {
    }

    public record WorldBorderSettings(boolean enabled, double lobbyRadius, double ingameRadius) {
    }

    public record Rewards(int kill, int death, int win) {
    }

    public static QuickSGConfig from(FileConfiguration yaml) {
        var config = BundledDefaults.strict(yaml);
        var countdown = config.section("countdown");
        var border = config.section("world-border");
        var rewards = config.section("rewards");
        return new QuickSGConfig(
                config.integer("players.max"),
                new Countdowns(
                        countdown.integer("freeze"),
                        countdown.integer("protection"),
                        countdown.integer("round"),
                        countdown.integer("tracker-after")),
                new WorldBorderSettings(
                        border.flag("enabled"),
                        border.decimal("lobby-radius"),
                        border.decimal("ingame-radius")),
                CompassTracker.Settings.from(yaml),
                new Rewards(rewards.integer("kill"), rewards.integer("death"), rewards.integer("win")),
                KitCatalog.from(section(yaml, "kits"), Kit.class),
                LootTable.fromYaml(section(yaml, "chest")),
                CombatStyle.from(yaml));
    }

    private static ConfigurationSection section(FileConfiguration config, String key) {
        var section = config.getConfigurationSection(key);
        return section != null ? section : config.createSection(key);
    }
}
