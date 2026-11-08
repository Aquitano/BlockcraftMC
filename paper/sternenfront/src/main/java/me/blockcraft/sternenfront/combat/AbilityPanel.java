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

/**
 * The ability tiles in the screen's bottom-right corner, in the pixels of the pack's {@code blockcraft:abilities} font:
 * a column against the corner, the first slot lowest, each tile with its key on a badge and the ability's name left of
 * it. The panel's origin is its top-right corner, so every glyph lies left of it and below it.
 */
final class AbilityPanel {

    static final Key FONT = Key.key("blockcraft", "abilities");

    /** What a tile's badge shows; a double jump shows the space bar twice. */
    enum Binding {
        F(10), Q(10), SHIFT_F(16), DOUBLE_JUMP(16);

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

    /** {@code stored} of {@code capacity} uses; an ability that holds several counts the stored ones on its tile. */
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
    /** A sixteen-pixel icon, as Minecraft draws an item, in the middle of the tile. */
    private static final int ICON = 16;
    private static final int ICON_INSET = (TILE - ICON) / 2;
    private static final int ICON_ADVANCE = ICON + 1;
    private static final int FILL_LEVELS = TILE - 2;
    private static final int DIGIT_ADVANCE = 9;
    /** A digit's dark rim overlaps the next one's. */
    private static final int DIGIT_PITCH = 7;
    private static final int DIGIT_WIDTH = 8;
    private static final int DIGIT_HEIGHT = 11;
    /** The seconds centred on the tile, the odd pixel above them. */
    private static final int DIGIT_TOP = (TILE - DIGIT_HEIGHT + 1) / 2;
    private static final int MAX_SECONDS = 99;
    /** The count's digits, each ringed dark, the rims overlapping like the seconds'. */
    private static final int COUNT_ADVANCE = 6;
    private static final int COUNT_PITCH = 4;
    private static final int COUNT_WIDTH = 5;
    private static final int COUNT_HEIGHT = 7;
    private static final int MAX_COUNT = 99;
    /** The name's capitals level with the tile's middle. */
    private static final int NAME_TOP = 8;
    private static final int NAME_GAP = 4;
    /** The widest name, cut short beyond it; the shader leaves the names out where the GUI has no room for this. */
    static final int NAME_WIDTH = 68;
    static final char BACKING = '\uE000';
    static final char FRAME = '\uE001';
    static final char FIRST_ICON = '\uE010';
    static final char FIRST_COOLING_ICON = '\uE030';
    static final char FIRST_BADGE = '\uE050';
    static final char FIRST_FILL = '\uE060';
    static final char FIRST_DIGIT = '\uE080';
    static final char FIRST_COUNT = '\uE090';

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

    /** The tile from its backing up: a recharging one adds its fill under a grey icon and its seconds over the frame. */
    private static List<Glyph> slot(Slot slot, int top, Tint side) {
        int left = -TILE;
        var glyphs = new ArrayList<Glyph>();
        glyphs.add(glyph(BACKING, left, top, Tint.WHITE, TILE_ADVANCE));
        switch (slot.state()) {
            case State.Ready _ -> glyphs.addAll(face(FIRST_ICON, slot, side, left, top));
            case State.Armed _ -> glyphs.addAll(face(FIRST_ICON, slot, Tint.SQUAD, left, top));
            case State.Cooling(int seconds, double done) -> {
                int level = (int) Math.round(Math.clamp(done, 0, 1) * FILL_LEVELS);
                if (level > 0) {
                    glyphs.add(glyph((char) (FIRST_FILL + level - 1), left, top, Tint.WHITE, TILE_ADVANCE));
                }
                glyphs.addAll(face(FIRST_COOLING_ICON, slot, Tint.DIMMED, left, top));
                var digits = String.valueOf(Math.clamp(seconds, 1, MAX_SECONDS));
                int width = DIGIT_WIDTH + (digits.length() - 1) * DIGIT_PITCH;
                for (int index = 0; index < digits.length(); index++) {
                    glyphs.add(glyph((char) (FIRST_DIGIT + digits.charAt(index) - '0'), left + (TILE - width) / 2 + index * DIGIT_PITCH,
                            top + DIGIT_TOP, Tint.WHITE, DIGIT_ADVANCE));
                }
            }
        }
        glyphs.addAll(count(slot.charges(), top));
        glyphs.add(glyph((char) (FIRST_BADGE + slot.binding().ordinal()), left, top, Tint.WHITE, slot.binding().advance));
        glyphs.addAll(label(slot, top));
        return glyphs;
    }

    /**
     * The uses stored, in the tile's bottom-right corner inside its frame as the hotbar counts a stack: only while the
     * ability holds several and has one left, since an empty one shows the seconds to its next.
     */
    private static List<Glyph> count(Charges charges, int top) {
        if (charges.capacity() <= 1 || charges.stored() <= 0) {
            return List.of();
        }
        var digits = String.valueOf(Math.min(charges.stored(), MAX_COUNT));
        int left = -1 - COUNT_WIDTH - (digits.length() - 1) * COUNT_PITCH;
        var glyphs = new ArrayList<Glyph>();
        for (int index = 0; index < digits.length(); index++) {
            glyphs.add(glyph((char) (FIRST_COUNT + digits.charAt(index) - '0'), left + index * COUNT_PITCH, top + TILE - 1 - COUNT_HEIGHT,
                    Tint.WHITE, COUNT_ADVANCE));
        }
        return glyphs;
    }

    /** The ability's icon from the run starting at {@code firstIcon}, framed in {@code frame}. */
    private static List<Glyph> face(char firstIcon, Slot slot, Tint frame, int left, int top) {
        return List.of(glyph((char) (firstIcon + slot.icon().ordinal()), left + ICON_INSET, top + ICON_INSET, Tint.WHITE, ICON_ADVANCE),
                glyph(FRAME, left, top, frame, TILE_ADVANCE));
    }

    /**
     * The name left of the tile, level with its middle. It stays white while the tile recharges, because grey letters
     * vanish against a bright floor.
     */
    private static List<Glyph> label(Slot slot, int top) {
        var name = HudPanel.fitted(HudPanel.capitals(slot.name()), NAME_WIDTH);
        return name.isEmpty() ? List.of() : List.of(HudPanel.label(name, -TILE - NAME_GAP - HudPanel.width(name), top + NAME_TOP, Tint.LABEL));
    }

    private static Glyph glyph(char glyph, int x, int y, Tint tint, int advance) {
        return new Glyph(String.valueOf(glyph), x, y, tint, false, advance);
    }
}
