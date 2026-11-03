package me.blockcraft.sternenfront.bot;

import org.bukkit.configuration.ConfigurationSection;

public record BotConfig(boolean enabled, int perTeam, int reactionTicks, int shotTicks, double spread,
                        double range, int killPoints) {

    public static BotConfig from(ConfigurationSection config, int capacity) {
        return new BotConfig(config.getBoolean("bots.enabled", true),
                Math.clamp(config.getInt("bots.fill-per-team", 4), 1, capacity),
                Math.max(1, config.getInt("bots.reaction-ticks", 10)),
                Math.max(1, config.getInt("bots.shot-ticks", 8)),
                Math.clamp(config.getDouble("bots.aim-spread", 0.065), 0, 1),
                Math.clamp(config.getDouble("bots.sight-range", 32), 4, 64),
                Math.max(0, config.getInt("bots.kill-points", 25)));
    }

    public int needed(int humans) {
        return enabled ? Math.max(0, perTeam - humans) : 0;
    }
}
