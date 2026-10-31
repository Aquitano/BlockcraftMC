package me.blockcraft.zombies.game;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Something that shakes up one wave. {@link WaveEvents} starts it, ticks it, and ends it no later than the wave. */
public sealed interface WaveEvent permits BloodMoon, PowerOutage, Bounty, ToxicFog {

    enum Kind {
        BLOOD_MOON, POWER_OUTAGE, BOUNTY, TOXIC_FOG;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    Kind kind();

    void start();

    /** Whether the event still runs. */
    boolean tick(int tick);

    default void killed(Mob mob, Player killer) { }

    /** Whether the event uses the player's action bar right now. */
    default boolean holding(Player player) {
        return false;
    }

    Component goal();

    /** Undoes what the event changed; the event, the wave or the round is over. */
    default void end() { }
}
