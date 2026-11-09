package me.blockcraft.story.game;

import java.util.Locale;

/** The party's one decision on Tessar, which picks the ending. */
public enum Choice {
    FABRIK, GEFANGENE;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
