package me.blockcraft.story.game;

import java.util.Locale;

/** Where the whole party stands in the mission; it only moves forward. */
public enum Stage {
    BRIEFING, RELAY, DEFENSE, REPORT, ENDING, DONE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
