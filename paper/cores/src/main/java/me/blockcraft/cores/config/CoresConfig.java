package me.blockcraft.cores.config;

import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.LootTable;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Cores' own keys of its config.yml; the lobby's are {@link me.blockcraft.api.game.GameConfig}'s. */
public record CoresConfig(int playersPerTeam, int fullSeconds, int roundSeconds, int killCoins, int coreCoins,
                          int winCoins, int alarmRadius, int alarmSeconds, int fatigueLevel,
                          int protectionSeconds, int bowKillArrows, int voidHeight, int buildHeight,
                          CombatStyle combat, List<ItemStack> kit, LootTable chests) {

    public static CoresConfig from(ConfigurationSection yaml) {
        var config = BundledDefaults.strict(yaml);
        return new CoresConfig(
                Math.max(1, config.integer("teams.players-per-team")),
                Math.max(1, config.integer("countdown.full")),
                Math.max(60, config.integer("countdown.round")),
                Math.max(0, config.integer("rewards.kill")),
                Math.max(0, config.integer("rewards.core")),
                Math.max(0, config.integer("rewards.win")),
                Math.max(1, config.integer("cores.alarm-radius")),
                Math.max(1, config.integer("cores.alarm-seconds")),
                Math.clamp(config.integer("cores.fatigue-level"), 0, 4),
                Math.max(0, config.integer("respawn.protection-seconds")),
                Math.max(0, config.integer("bow-kill-arrows")),
                config.integer("void-height"),
                config.integer("build-height"),
                CombatStyle.from(yaml),
                yaml.getMapList("kit").stream().map(CoresConfig::item).toList(),
                LootTable.fromYaml(yaml.getConfigurationSection("chests")));
    }

    private static ItemStack item(Map<?, ?> entry) {
        return ItemStack.of(Material.valueOf(String.valueOf(entry.get("item"))),
                entry.get("amount") instanceof Number amount ? amount.intValue() : 1);
    }

    public int capacity() {
        return playersPerTeam * 2;
    }
}
