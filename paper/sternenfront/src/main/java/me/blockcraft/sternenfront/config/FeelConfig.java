package me.blockcraft.sternenfront.config;

import org.bukkit.configuration.ConfigurationSection;

/**
 * How long a killed player watches their killer ({@code killcam.ticks}), how many kills in one life earn an orbital
 * strike ({@code streaks.orbital-strike}), and how aiming and a long rifle's scope slow the walk and narrow the view
 * ({@code aim}).
 */
public record FeelConfig(int killCamTicks, int orbitalStreak, int botsPerStreakKill, Aim aim) {

    /**
     * {@code zoom} is the share of the view left while aiming; a long rifle leaves {@code scopeZoom} of it and walks at
     * {@code scopedWalk} of the normal pace.
     */
    public record Aim(double zoom, double scopeZoom, double scopedWalk) {

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
                new Aim(zoom, Math.clamp(config.getDouble("aim.scope-zoom", 0.25), 0.1, 0.45),
                        Math.clamp(config.getDouble("aim.scoped-walk", 0.3), 0, 1)));
    }
}
