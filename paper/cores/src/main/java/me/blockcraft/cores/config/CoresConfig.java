package me.blockcraft.cores.config;

import me.blockcraft.api.game.CombatStyle;
import me.blockcraft.api.game.LootTable;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Cores' own keys of its config.yml; the lobby's are {@link me.blockcraft.api.game.GameConfig}'s. */
public record CoresConfig(int playersPerTeam, int fullSeconds, int roundSeconds, int killCoins, int coreCoins,
                          int winCoins, int alarmRadius, int alarmSeconds, int fatigueRadius, int fatigueLevel,
                          int protectionSeconds, int bowKillArrows, int voidHeight, int buildHeight,
                          CombatStyle combat, List<ItemStack> kit, LootTable chests) {

    public static CoresConfig from(ConfigurationSection config) {
        var chests = config.getConfigurationSection("chests");
        return new CoresConfig(
                Math.max(1, config.getInt("teams.players-per-team", 4)),
                Math.max(1, config.getInt("countdown.full", 20)),
                Math.max(60, config.getInt("countdown.round", 2700)),
                Math.max(0, config.getInt("rewards.kill", 5)),
                Math.max(0, config.getInt("rewards.core", 15)),
                Math.max(0, config.getInt("rewards.win", 25)),
                Math.max(1, config.getInt("cores.alarm-radius", 8)),
                Math.max(1, config.getInt("cores.alarm-seconds", 10)),
                Math.max(1, config.getInt("cores.fatigue-radius", 4)),
                Math.clamp(config.getInt("cores.fatigue-level", 0), 0, 4),
                Math.max(0, config.getInt("respawn.protection-seconds", 5)),
                Math.max(0, config.getInt("bow-kill-arrows", 4)),
                config.getInt("void-height", 40),
                config.getInt("build-height", 100),
                CombatStyle.from(config),
                config.getMapList("kit").stream().map(CoresConfig::item).toList(),
                chests == null ? new LootTable(0, 0, List.of()) : LootTable.fromYaml(chests, 3, 6));
    }

    private static ItemStack item(Map<?, ?> entry) {
        return ItemStack.of(Material.valueOf(String.valueOf(entry.get("item"))),
                entry.get("amount") instanceof Number amount ? amount.intValue() : 1);
    }

    public int capacity() {
        return playersPerTeam * 2;
    }
}
