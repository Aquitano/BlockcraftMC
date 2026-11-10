package me.blockcraft.sternenfront.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * How long a killed player watches their killer ({@code killcam.ticks}), how many kills in one life earn an orbital
 * strike ({@code streaks.orbital-strike}), and how aiming and a scope slow the walk and narrow the view ({@code aim}).
 */
public record FeelConfig(int killCamTicks, int orbitalStreak, int botsPerStreakKill, Aim aim) {

    /** {@code zoom} is the share of the view left while aiming and {@code scopedWalk} the share of the normal pace in a scope. */
    public record Aim(double zoom, double scopedWalk) {

        /**
         * The share of its speed the walk keeps while aiming, which is what narrows a client's view: it draws
         * {@code (speed share + 1) / 2} of its field of view.
         */
        public double speedShare() {
            return 2 * zoom - 1;
        }
    }

    public static FeelConfig from(ConfigurationSection config) {
        double zoom = Math.clamp(config.getDouble("aim.zoom", 0.92), 0.55, 1);
        return new FeelConfig(
                Math.max(0, config.getInt("killcam.ticks", 50)),
                Math.max(1, config.getInt("streaks.orbital-strike", 5)),
                Math.max(1, config.getInt("streaks.bots-per-kill", 3)),
                new Aim(zoom, Math.clamp(config.getDouble("aim.scoped-walk", 0.3), 0, 1)));
    }
}
