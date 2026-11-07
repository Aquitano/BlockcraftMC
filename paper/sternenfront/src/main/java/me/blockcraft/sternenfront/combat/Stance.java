package me.blockcraft.sternenfront.combat;

/** What a bladed hero's body shows other players. */
public sealed interface Stance {

    enum Still implements Stance {
        /** The blade held out in front, tipped forward. */
        READY,
        /** Both hands on the hilt, the blade across the chest. */
        GUARD,
        /** The off hand thrown forward for a Force power. */
        CAST
    }

    /** A sweep that starts on the hero's left if {@code fromLeft}, {@code elapsed} of its {@code ticks} in. */
    record Swing(boolean fromLeft, int elapsed, int ticks) implements Stance { }
}
