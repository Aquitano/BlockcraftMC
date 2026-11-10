package me.blockcraft.lobby.activity;

import java.util.List;

/** The refrain of Jingle Bells on the note block's scale, for whatever plays it at Christmas. */
final class JingleBells {

    record Note(long tick, int semitone) { }

    /** The refrain lasts 52 ticks; a loop waits a little longer before it starts over. */
    static final long LOOP_TICKS = 64;

    static final List<Note> REFRAIN = List.of(
            new Note(0, 10), new Note(4, 10), new Note(8, 10),
            new Note(16, 10), new Note(20, 10), new Note(24, 10),
            new Note(32, 10), new Note(36, 13), new Note(40, 6), new Note(44, 8), new Note(48, 10));

    private JingleBells() {
    }
}
