package me.blockcraft.lobby.konami;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The music of the code, in note-block semitones (0 is F♯3). Each key climbs the E-flat major scale up to its leading
 * tone, and the full code resolves into the 1-UP run of Super Mario Bros., a semitone below the original so its top
 * note still fits the note block, under an E-flat major chord.
 */
final class Chiptune {

    private record Note(long tick, Sounds sound, int semitone) {
    }

    private static final List<Integer> STEPS = List.of(6, 8, 9, 11, 13, 14, 16, 18, 20);
    private static final int MISS = 3;
    private static final List<Note> POWER_UP = List.of(
            new Note(0, Sounds.NOTE_BIT, 9),
            new Note(3, Sounds.NOTE_BIT, 12),
            new Note(6, Sounds.NOTE_BIT, 21),
            new Note(9, Sounds.NOTE_BIT, 17),
            new Note(12, Sounds.NOTE_BIT, 19),
            new Note(15, Sounds.NOTE_BIT, 24),
            new Note(20, Sounds.NOTE_PLING, 13),
            new Note(20, Sounds.NOTE_PLING, 16),
            new Note(20, Sounds.NOTE_PLING, 21));

    private Chiptune() {
    }

    /** The note for a run of {@code matched} keys, short of the full code. */
    static void step(Player player, int matched) {
        Sounds.NOTE_BIT.note(player, STEPS.get(Math.clamp(matched, 1, STEPS.size()) - 1));
    }

    static void miss(Player player) {
        Sounds.NOTE_BASS.note(player, MISS);
    }

    static void powerUp(Plugin plugin, Player player) {
        for (var note : POWER_UP) {
            Tasks.later(plugin, note.tick(), () -> {
                if (player.isOnline()) {
                    note.sound().note(player, note.semitone());
                }
            });
        }
    }
}
