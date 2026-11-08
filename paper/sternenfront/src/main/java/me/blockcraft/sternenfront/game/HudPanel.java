package me.blockcraft.sternenfront.game;

import me.blockcraft.api.pack.UiFont;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;

/**
 * Glyphs the pack's text shader moves into a corner of the screen. Each glyph's colour names the corner, its row below
 * the panel's top and its tint, so the glyphs' own heights never move them; {@code hud_panel.glsl} lists those colours.
 */
public final class HudPanel {

    /** Where the shader puts a panel, and the line that carries it there. */
    public enum Corner {
        /** On a boss bar line of its own. */
        TOP_LEFT(231),
        /** On the action bar, starting in the line's middle. */
        BOTTOM_RIGHT(232),
        /** On a boss bar line like the top-left, from the corner leftwards. */
        TOP_RIGHT(233);

        final int red;

        Corner(int red) {
            this.red = red;
        }
    }

    /** The shader's colours for a glyph, in the order of its tint table; the factions' are those of {@link Faction#text()}. */
    public enum Tint {
        WHITE, DIMMED, REPUBLIC, SEPARATISTS, SQUAD, SHADOW, ACCENT;

        public static Tint of(Faction faction) {
            return faction == Faction.KOLONIEN ? REPUBLIC : SEPARATISTS;
        }
    }

    /** {@code text} drawn from {@code x} pixels after the panel's origin and {@code y} below its top. */
    public record Glyph(String text, int x, int y, Tint tint, boolean shadow, int advance) { }

    static final int FIRST_TINT = 176;
    /** The menu tiles' capitals, which the panels' fonts share, with an underscore of the panels' own. */
    private static final String CAPITALS = UiFont.LABEL_LETTERS + "_";
    /** The capitals the panels' fonts draw two pixels wider, so their diagonals show; a three-pixel M reads as an N. */
    private static final String WIDE_LETTERS = "MNW";
    private static final int WIDE_ADVANCE = UiFont.LABEL_ADVANCE + 2;

    private HudPanel() {
    }

    /**
     * The glyphs as one line that ends where it began, so the client starts it where the panel's origin is meant to
     * be whatever it shows. Each glyph's colour carries its row and tint; its shadow, when it has one, carries the same row.
     */
    public static Component render(Corner corner, Key font, List<Glyph> glyphs) {
        var line = Component.text();
        int cursor = 0;
        for (var glyph : glyphs) {
            if (glyph.x() != cursor) {
                line.append(UiFont.space(glyph.x() - cursor));
            }
            line.append(Component.text(glyph.text(), colour(corner, glyph.y(), glyph.tint())).font(font).shadowColor(glyph.shadow()
                    ? ShadowColor.shadowColor(0xFF000000 | colour(corner, glyph.y(), Tint.SHADOW).value()) : ShadowColor.none()));
            cursor = glyph.x() + glyph.advance();
        }
        if (cursor != 0) {
            line.append(UiFont.space(-cursor));
        }
        return line.build();
    }

    /** {@code text} in the capitals, with a question mark for any letter they lack. */
    public static String capitals(String text) {
        return UiFont.capitals(text, CAPITALS);
    }

    /** How wide the panels' fonts draw {@code capitals}. */
    public static int width(String capitals) {
        return capitals.codePoints().map(letter -> WIDE_LETTERS.indexOf(letter) >= 0 ? WIDE_ADVANCE : UiFont.LABEL_ADVANCE).sum();
    }

    /** {@code capitals} as a line of text on a panel, shadowed so it reads over a bright floor. */
    public static Glyph label(String capitals, int x, int y, Tint tint) {
        return new Glyph(capitals, x, y, tint, true, width(capitals));
    }

    private static TextColor colour(Corner corner, int row, Tint tint) {
        return TextColor.color(corner.red, row, FIRST_TINT + tint.ordinal());
    }
}
