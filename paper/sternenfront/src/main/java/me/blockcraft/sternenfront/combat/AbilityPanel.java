package me.blockcraft.sternenfront.combat;

import me.blockcraft.api.pack.UiFont;
import me.blockcraft.sternenfront.game.AbilityIcon;
import me.blockcraft.sternenfront.game.HudPanel;
import me.blockcraft.sternenfront.game.HudPanel.Corner;
import me.blockcraft.sternenfront.game.HudPanel.Glyph;
import me.blockcraft.sternenfront.game.HudPanel.Tint;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The ability tiles in the screen's bottom-right corner, in the pixels of the pack's {@code blockcraft:abilities} font:
 * a column against the corner, the first slot lowest, each tile with its key on a badge and the ability's name left of
 * it. The panel's origin is its top-right corner, so every glyph lies left of it and below it.
 */
final class AbilityPanel {

    static final Key FONT = Key.key("blockcraft", "abilities");

    /** What a tile's badge shows. */
    enum Binding {
        F(10), Q(10), SHIFT_F(16), DOUBLE_JUMP(10);

        private final int advance;

        Binding(int advance) {
            this.advance = advance;
        }
    }

    sealed interface State {
        /** Bright with the side's frame, ready to use. */
        record Ready() implements State { }

        /** Grey with the seconds left, behind a fill that rises as the ability recharges, {@code done} of the way. */
        record Cooling(int seconds, double done) implements State { }

        /** Framed in gold: a reward that waits to be used. */
        record Armed() implements State { }
    }

    /** {@code stored} of {@code capacity} uses, shown as pips under the name while the ability holds more than one. */
    record Charges(int stored, int capacity) {
        static final Charges ONE = new Charges(1, 1);
    }

    record Slot(Binding binding, AbilityIcon icon, String name, State state, Charges charges) { }

    static final int MAX_SLOTS = 4;
    static final int TILE = 24;
    private static final int PITCH = TILE + 4;
    /** The column with every slot filled; the pack's shader puts the panel's top that far above its corner. */
    static final int HEIGHT = MAX_SLOTS * PITCH - (PITCH - TILE);
    private static final int TILE_ADVANCE = TILE + 1;
    private static final int ICON_INSET = 2;
    private static final int ICON_ADVANCE = 21;
    private static final int FILL_LEVELS = TILE - 2;
    private static final int DIGIT_ADVANCE = 9;
    /** A digit's dark rim overlaps the next one's. */
    private static final int DIGIT_PITCH = 7;
    private static final int DIGIT_WIDTH = 8;
    private static final int DIGIT_HEIGHT = 11;
    private static final int MAX_SECONDS = 99;
    private static final int PIP_ADVANCE = 7;
    /** The pack draws these capitals two pixels wider, so their diagonals show. */
    private static final String WIDE_LETTERS = "MNW";
    private static final int WIDE_LETTER_ADVANCE = 6;
    private static final int NAME_GAP = 4;
    static final char BACKING = '\uE000';
    static final char FRAME = '\uE001';
    static final char FIRST_ICON = '\uE010';
    static final char FIRST_COOLING_ICON = '\uE030';
    static final char FIRST_BADGE = '\uE050';
    static final char FIRST_FILL = '\uE060';
    static final char FIRST_DIGIT = '\uE080';
    static final char PIP_HELD = '\uE090';
    private static final char PIP_SPENT = PIP_HELD + 1;

    private AbilityPanel() {
    }

    /** {@code side} frames the tiles that are ready. */
    static Component render(List<Slot> slots, Tint side) {
        return HudPanel.render(Corner.BOTTOM_RIGHT, FONT, layout(slots, side));
    }

    static List<Glyph> layout(List<Slot> slots, Tint side) {
        var glyphs = new ArrayList<Glyph>();
        for (int index = 0; index < Math.min(MAX_SLOTS, slots.size()); index++) {
            glyphs.addAll(slot(slots.get(index), HEIGHT - TILE - index * PITCH, side));
        }
        return glyphs;
    }

    private static List<Glyph> slot(Slot slot, int top, Tint side) {
        var glyphs = new ArrayList<Glyph>();
        int left = -TILE;
        boolean cooling = slot.state() instanceof State.Cooling;
        glyphs.add(glyph(BACKING, left, top, Tint.WHITE, TILE_ADVANCE));
        if (slot.state() instanceof State.Cooling(int seconds, double done)) {
            int level = (int) Math.round(Math.clamp(done, 0, 1) * FILL_LEVELS);
            if (level > 0) {
                glyphs.add(glyph((char) (FIRST_FILL + level - 1), left, top, Tint.WHITE, TILE_ADVANCE));
            }
        }
        glyphs.add(glyph((char) ((cooling ? FIRST_COOLING_ICON : FIRST_ICON) + slot.icon().ordinal()), left + ICON_INSET,
                top + ICON_INSET, Tint.WHITE, ICON_ADVANCE));
        glyphs.add(glyph(FRAME, left, top, switch (slot.state()) {
            case State.Ready _ -> side;
            case State.Cooling _ -> Tint.DIMMED;
            case State.Armed _ -> Tint.SQUAD;
        }, TILE_ADVANCE));
        if (slot.state() instanceof State.Cooling(int seconds, double done)) {
            var digits = String.valueOf(Math.clamp(seconds, 1, MAX_SECONDS));
            int width = DIGIT_WIDTH + (digits.length() - 1) * DIGIT_PITCH;
            for (int index = 0; index < digits.length(); index++) {
                glyphs.add(glyph((char) (FIRST_DIGIT + digits.charAt(index) - '0'), left + (TILE - width) / 2 + index * DIGIT_PITCH,
                        top + (TILE - DIGIT_HEIGHT) / 2 + 1, Tint.WHITE, DIGIT_ADVANCE));
            }
        }
        glyphs.add(glyph((char) (FIRST_BADGE + slot.binding().ordinal()), left, top, Tint.WHITE, slot.binding().advance));
        glyphs.addAll(label(slot, top));
        return glyphs;
    }

    /**
     * The name left of the tile, level with its middle, or above the pips when the ability holds several uses. It stays
     * white while the tile recharges, because grey letters vanish against a bright floor.
     */
    private static List<Glyph> label(Slot slot, int top) {
        var glyphs = new ArrayList<Glyph>();
        var name = HudPanel.capitals(slot.name());
        int right = -TILE - NAME_GAP;
        var charges = slot.charges();
        boolean pips = charges.capacity() > 1;
        int width = name.chars().map(letter -> WIDE_LETTERS.indexOf(letter) >= 0 ? WIDE_LETTER_ADVANCE : UiFont.LABEL_ADVANCE).sum();
        if (!name.isEmpty()) {
            glyphs.add(new Glyph(name, right - width, top + (pips ? 4 : 8), Tint.WHITE, true, width));
        }
        if (pips) {
            int x = right - charges.capacity() * PIP_ADVANCE + 1;
            for (int pip = 0; pip < charges.capacity(); pip++) {
                glyphs.add(glyph(pip < charges.stored() ? PIP_HELD : PIP_SPENT, x + pip * PIP_ADVANCE, top + 14, Tint.WHITE, PIP_ADVANCE));
            }
        }
        return glyphs;
    }

    private static Glyph glyph(char glyph, int x, int y, Tint tint, int advance) {
        return new Glyph(String.valueOf(glyph), x, y, tint, false, advance);
    }
}
