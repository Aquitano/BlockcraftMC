package me.blockcraft.sternenfront.combat;

import me.blockcraft.sternenfront.game.AbilityIcon;
import me.blockcraft.sternenfront.game.HudPanel;
import me.blockcraft.sternenfront.game.HudPanel.Corner;
import me.blockcraft.sternenfront.game.HudPanel.Glyph;
import me.blockcraft.sternenfront.game.HudPanel.Tint;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The ability tiles in the screen's bottom-right corner, in the pixels of the pack's {@code blockcraft:abilities} font.
 * The first slot stands against the corner and each next one left of it, every tile under a keycap naming its key.
 * The panel's origin is its top-right corner, so every glyph lies left of it.
 */
final class AbilityPanel {

    static final Key FONT = Key.key("blockcraft", "abilities");

    /** What a tile's keycap shows. */
    enum Binding {
        F(''), Q(''), SHIFT_F('', ''), DOUBLE_JUMP('');

        private final char[] symbols;

        Binding(char... symbols) {
            this.symbols = symbols;
        }
    }

    sealed interface State {
        /** Bright, ready to use. */
        record Ready() implements State { }

        /** Bright with the uses left, while at least one is. */
        record Charged(int stored, int capacity) implements State { }

        /** Dimmed with the seconds until it can be used again. */
        record Cooling(int seconds) implements State { }

        /** Ringed in gold: a reward that waits to be used. */
        record Armed() implements State { }
    }

    /** A tile with no icon is a key with nothing bound to it, drawn dimmed. */
    record Slot(Binding binding, Optional<AbilityIcon> icon, State state) { }

    static final int TILE = 20;
    private static final int PITCH = TILE + 3;
    private static final int TILE_TOP = 6;
    private static final int ICON_INSET = 2;
    private static final char TILE_GLYPH = '';
    private static final char FIRST_ICON = '';
    private static final char KEYCAP = '';
    private static final char WIDE_KEYCAP = '';
    private static final char FIRST_DIGIT = '';
    private static final int TILE_ADVANCE = TILE + 1;
    private static final int ICON_ADVANCE = 17;
    private static final int[] KEYCAP_WIDTHS = {9, 15};
    private static final int SYMBOL_ADVANCE = 6;
    private static final int DIGIT_HEIGHT = 7;
    private static final char FIRST_COUNT_DIGIT = '\uE050';
    private static final char COUNT_SLASH = '\uE05A';
    private static final int COUNT_WIDTH = 5;
    private static final int COUNT_HEIGHT = 7;
    /** A count glyph's dark rim overlaps the next one's. */
    private static final int COUNT_PITCH = 4;
    private static final int MAX_SECONDS = 99;

    private AbilityPanel() {
    }

    static Component render(List<Slot> slots) {
        return HudPanel.render(Corner.BOTTOM_RIGHT, FONT, layout(slots));
    }

    static List<Glyph> layout(List<Slot> slots) {
        var glyphs = new ArrayList<Glyph>();
        for (int index = 0; index < slots.size(); index++) {
            glyphs.addAll(slot(slots.get(index), -TILE - index * PITCH));
        }
        return glyphs;
    }

    private static List<Glyph> slot(Slot slot, int left) {
        var glyphs = new ArrayList<Glyph>();
        var state = slot.state();
        boolean bright = slot.icon().isPresent() && !(state instanceof State.Cooling);
        glyphs.add(new Glyph(String.valueOf(TILE_GLYPH), left, TILE_TOP, state instanceof State.Armed ? Tint.SQUAD
                : bright ? Tint.WHITE : Tint.DIMMED, false, TILE_ADVANCE));
        slot.icon().ifPresent(icon -> glyphs.add(new Glyph(String.valueOf((char) (FIRST_ICON + icon.ordinal())),
                left + ICON_INSET, TILE_TOP + ICON_INSET, bright ? Tint.WHITE : Tint.DIMMED, false, ICON_ADVANCE)));
        glyphs.addAll(keycap(slot.binding(), left));
        if (slot.icon().isEmpty()) {
            return glyphs;
        }
        switch (state) {
            case State.Cooling cooling -> {
                var digits = digits(Math.clamp(cooling.seconds(), 1, MAX_SECONDS));
                int width = digits.length() * SYMBOL_ADVANCE - 1;
                glyphs.add(new Glyph(digits, left + (TILE - width) / 2, TILE_TOP + (TILE - DIGIT_HEIGHT) / 2, Tint.WHITE, true,
                        digits.length() * SYMBOL_ADVANCE));
            }
            case State.Charged charged -> {
                var count = charged.stored() + "/" + charged.capacity();
                int x = left + TILE - 1 - (count.length() - 1) * COUNT_PITCH - COUNT_WIDTH;
                for (int index = 0; index < count.length(); index++) {
                    char character = count.charAt(index);
                    char glyph = character == '/' ? COUNT_SLASH : (char) (FIRST_COUNT_DIGIT + character - '0');
                    glyphs.add(new Glyph(String.valueOf(glyph), x + index * COUNT_PITCH, TILE_TOP + TILE - 1 - COUNT_HEIGHT,
                            Tint.WHITE, false, COUNT_WIDTH + 1));
                }
            }
            case State.Ready _, State.Armed _ -> { }
        }
        return glyphs;
    }

    private static List<Glyph> keycap(Binding binding, int left) {
        boolean wide = binding.symbols.length > 1;
        int width = KEYCAP_WIDTHS[wide ? 1 : 0];
        int x = left + (TILE - width) / 2;
        var glyphs = new ArrayList<Glyph>();
        glyphs.add(new Glyph(String.valueOf(wide ? WIDE_KEYCAP : KEYCAP), x, 0, Tint.WHITE, false, width + 1));
        for (int symbol = 0; symbol < binding.symbols.length; symbol++) {
            glyphs.add(new Glyph(String.valueOf(binding.symbols[symbol]), x + 2 + symbol * SYMBOL_ADVANCE, 1, Tint.SHADOW, false,
                    SYMBOL_ADVANCE));
        }
        return glyphs;
    }

    private static String digits(int number) {
        var digits = new StringBuilder();
        String.valueOf(number).chars().forEach(digit -> digits.append((char) (FIRST_DIGIT + digit - '0')));
        return digits.toString();
    }
}
