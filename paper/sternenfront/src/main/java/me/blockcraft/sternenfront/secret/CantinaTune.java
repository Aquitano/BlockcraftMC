package me.blockcraft.sternenfront.secret;

import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The music box's tune, written for it: a minor swing in D for a band of note block sounds. Four bars of rhythm
 * section, then sixteen bars of melody (A A' B A) over a walking bass, chords on the offbeats and drums. A beat is six
 * ticks and a swung pair of eighths four and two, so the twenty bars run 24 seconds.
 */
final class CantinaTune {

    /** The note block sounds the band plays; each turns into its sound only when it plays. */
    enum Instrument {
        BIT, BASS, GUITAR, KICK, SNARE, HAT;

        Sound sound() {
            return switch (this) {
                case BIT -> Sound.BLOCK_NOTE_BLOCK_BIT;
                case BASS -> Sound.BLOCK_NOTE_BLOCK_BASS;
                case GUITAR -> Sound.BLOCK_NOTE_BLOCK_GUITAR;
                case KICK -> Sound.BLOCK_NOTE_BLOCK_BASEDRUM;
                case SNARE -> Sound.BLOCK_NOTE_BLOCK_SNARE;
                case HAT -> Sound.BLOCK_NOTE_BLOCK_HAT;
            };
        }
    }

    record Note(int tick, Instrument instrument, float pitch, float volume) {
    }

    /**
     * One instrument's part: its notes and rests in order as {@code name/ticks}, a chord as names joined by
     * {@code +}, {@code r} a rest and {@code x} a drum hit. {@code pitchOne} is the MIDI note the instrument plays at
     * pitch 1, the middle of its two octaves.
     */
    record Voice(Instrument instrument, int pitchOne, float volume, String score) {
    }

    static final int BAR = 24;
    static final int BARS = 20;

    private static final String DM = "D3+F3+A3";
    private static final String GM = "G3+Bb3+D4";
    private static final String A7 = "G3+C#4+E4";
    private static final String F = "F3+A3+C4";
    private static final String C7 = "E3+G3+Bb3";
    private static final String BB7 = "F3+Ab3+D4";

    static final List<Voice> VOICES = List.of(
            new Voice(Instrument.BIT, 66, 1f, """
                    r/24 | r/24 | r/24 | r/12 A3/4 C#4/2 E4/6 |
                    D4/4 F4/2 A4/6 Bb4/4 A4/2 F4/6 | G4/4 Bb4/2 D5/6 C5/4 Bb4/2 G4/6 |
                    A4/4 C#5/2 E5/4 D5/2 C#5/4 Bb4/2 A4/6 | D5/6 A4/4 F4/2 D4/6 r/6 |
                    D4/4 F4/2 A4/6 Bb4/4 A4/2 F4/6 | G4/4 Bb4/2 D5/6 D5/4 E5/2 F5/6 |
                    F5/4 D5/2 Bb4/4 Ab4/2 A4/4 C#5/2 E5/6 | D5/4 A4/2 F4/4 E4/2 D4/6 r/6 |
                    A4/4 C5/2 F5/6 E5/4 C5/2 A4/6 | G4/4 Bb4/2 E5/6 D5/4 Bb4/2 G4/6 |
                    F4/4 A4/2 C5/4 F5/2 E5/4 D5/2 C5/6 | C#5/4 E5/2 A4/4 G4/2 E4/4 C#4/2 A3/6 |
                    D4/4 F4/2 A4/6 Bb4/4 A4/2 F4/6 | G4/4 Bb4/2 D5/6 F5/4 E5/2 D5/6 |
                    E5/4 C#5/2 A4/4 Bb4/2 A4/4 G4/2 E4/6 | A4/4 F4/2 D5/6 D4/6 r/6
                    """),
            new Voice(Instrument.BASS, 42, 0.9f, """
                    D2/6 A2/6 D2/6 A2/6 | D2/6 A2/6 D2/6 C#2/6 | D2/6 A2/6 D2/6 A2/6 | A1/6 C#2/6 E2/6 G2/6 |
                    D2/6 F2/6 A2/6 C3/6 | G2/6 Bb2/6 D3/6 Bb2/6 | A2/6 C#3/6 E3/6 C#3/6 | D2/6 A2/6 D3/6 A2/6 |
                    D2/6 F2/6 A2/6 C3/6 | G2/6 Bb2/6 D3/6 Bb2/6 | Bb1/6 F2/6 A1/6 E2/6 | D2/6 A2/6 D3/6 A2/6 |
                    F2/6 A2/6 C3/6 A2/6 | C2/6 E2/6 G2/6 Bb2/6 | F2/6 A2/6 C3/6 A2/6 | A1/6 C#2/6 E2/6 G2/6 |
                    D2/6 F2/6 A2/6 C3/6 | G2/6 Bb2/6 D3/6 Bb2/6 | A2/6 C#3/6 E3/6 C#3/6 | D2/6 A1/6 D2/6 r/6
                    """),
            new Voice(Instrument.GUITAR, 54, 0.45f, comping(DM, DM, DM, A7, DM, GM, A7, DM, DM, GM, BB7 + " " + A7,
                    DM, F, C7, F, A7, DM, GM, A7, DM)),
            new Voice(Instrument.KICK, 66, 0.7f, "x/6 r/6 x/6 r/6 ".repeat(BARS)),
            new Voice(Instrument.SNARE, 66, 0.5f, "r/6 x/6 r/6 x/6 ".repeat(BARS)),
            new Voice(Instrument.HAT, 66, 0.35f, "r/4 x/2 r/4 x/2 r/4 x/2 r/4 x/2 ".repeat(BARS)));

    static final List<Note> NOTES = VOICES.stream().flatMap(voice -> notes(voice).stream())
            .sorted(Comparator.comparingInt(Note::tick)).toList();

    private CantinaTune() {
    }

    /** Chords on beats two and four, one bar per entry; an entry of two chords splits its bar between them. */
    private static String comping(String... bars) {
        var score = new StringBuilder();
        for (var bar : bars) {
            var chords = bar.split(" ");
            score.append("r/6 ").append(chords[0]).append("/6 r/6 ").append(chords[chords.length - 1]).append("/6 ");
        }
        return score.toString();
    }

    /** How many ticks the voice's score lasts. */
    static int length(Voice voice) {
        return tokens(voice).stream().mapToInt(token -> Integer.parseInt(token.substring(token.indexOf('/') + 1))).sum();
    }

    static List<Note> notes(Voice voice) {
        var notes = new ArrayList<Note>();
        int tick = 0;
        for (var token : tokens(voice)) {
            int slash = token.indexOf('/');
            var sound = token.substring(0, slash);
            if (sound.equals("x")) {
                notes.add(new Note(tick, voice.instrument(), 1f, voice.volume()));
            } else if (!sound.equals("r")) {
                for (var name : sound.split("\\+")) {
                    notes.add(new Note(tick, voice.instrument(), (float) Math.pow(2, (midi(name) - voice.pitchOne()) / 12.0), voice.volume()));
                }
            }
            tick += Integer.parseInt(token.substring(slash + 1));
        }
        return notes;
    }

    private static List<String> tokens(Voice voice) {
        return List.of(voice.score().trim().split("[\\s|]+"));
    }

    /** A note name such as {@code C#4} or {@code Bb2} as a MIDI number; C4 is 60. */
    static int midi(String name) {
        int semitone = switch (name.charAt(0)) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            case 'B' -> 11;
            default -> throw new IllegalArgumentException("Not a note: " + name);
        };
        int octaveAt = 1;
        if (name.charAt(1) == '#') {
            semitone++;
            octaveAt++;
        } else if (name.charAt(1) == 'b') {
            semitone--;
            octaveAt++;
        }
        return 12 * (Integer.parseInt(name.substring(octaveAt)) + 1) + semitone;
    }
}
