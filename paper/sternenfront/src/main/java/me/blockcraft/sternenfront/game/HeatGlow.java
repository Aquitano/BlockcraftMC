package me.blockcraft.sternenfront.game;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The colour of a blaster's heat vents, which the pack multiplies into their glowing faces. The heat shows in a few
 * steps rather than a gradient, so the held item changes only when the gun crosses one.
 */
public enum HeatGlow {
    COLD(0x4C535B), WARM(0xC85A1C), HOT(0xFFA23E), VENTING(0xFFE6C2), VENTING_DIM(0xE0702A);

    /** A gun glows warm from {@code warm} of full heat and hot from {@code hot}; a vent pulses every {@code pulseTicks}. */
    public record Steps(double warm, double hot, int pulseTicks) {

        public static Steps from(ConfigurationSection heat) {
            double hot = Math.clamp(heat.getDouble("warning-level"), 0.1, 0.99);
            return new Steps(Math.clamp(heat.getDouble("glow.warm"), 0, hot), hot, Math.max(1, heat.getInt("glow.pulse-ticks")));
        }
    }

    private final Color colour;

    HeatGlow(int rgb) {
        colour = Color.fromRGB(rgb);
    }

    public Color colour() {
        return colour;
    }

    public static HeatGlow of(Steps steps, double level, boolean venting, long tick) {
        if (venting) {
            return tick / steps.pulseTicks() % 2 == 0 ? VENTING : VENTING_DIM;
        }
        return level >= steps.hot() ? HOT : level >= steps.warm() ? WARM : COLD;
    }
}
