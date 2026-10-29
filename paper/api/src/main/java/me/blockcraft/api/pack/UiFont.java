package me.blockcraft.api.pack;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Locale;

/**
 * The glyphs of the resource pack's {@code blockcraft:ui} font, mirrored from {@code resourcepack/src/assets/blockcraft/font/ui.json}.
 * A menu panel is one glyph as tall as the whole chest screen, drawn in the title line and moved back to the
 * screen's corner with negative-width spaces; the title text then follows on top of it. The toolbar variant draws
 * three wide buttons instead of the first row of slots.
 */
public final class UiFont {

    public static final Key KEY = Key.key("blockcraft", "ui");

    /** The chest screen is 176 pixels wide and its title starts 8 pixels in; a glyph is followed by one pixel of spacing. */
    private static final int PANEL_WIDTH = 176;
    private static final int TITLE_X = 8;
    private static final int GLYPH_SPACING = 1;
    private static final int MAX_ROWS = 6;
    private static final String LABEL_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ÄÖÜ-.:!?…/+& ";

    private static final char PANEL_BASE = '\uE100';
    private static final char TOOLBAR_PANEL_BASE = '\uE110';
    private static final char CARD_PANEL_BASE = '\uE120';
    private static final char SHIFT_LEFT_BASE = '\uE800';
    private static final char SHIFT_RIGHT_BASE = '\uE810';
    private static final int SHIFT_BITS = 8;

    private UiFont() {
    }

    /** The panel for a chest with {@code rows} rows, then {@code title} where the vanilla title would be. */
    public static Component menuTitle(int rows, boolean toolbar, Component title) {
        return menuTitle(rows, toolbar, false, title);
    }

    public static Component menuTitle(int rows, boolean toolbar, boolean cards, Component title) {
        return Component.text()
                .append(Component.text(shift(-TITLE_X) + (cards ? (char) (CARD_PANEL_BASE + rows) : panel(rows, toolbar))
                        + shift(TITLE_X - PANEL_WIDTH - GLYPH_SPACING))
                        .font(KEY)
                        .color(NamedTextColor.WHITE))
                .append(title)
                .build();
    }

    public static Component tile(MenuTile.Placement tile, Component label) {
        int x = 8 + tile.slot() % 9 * 18;
        boolean wide = tile.shape() == MenuTile.Shape.WIDE;
        int labelX = x + (wide ? 26 : 3);
        int maxLetters = wide ? 11 : 12;
        var normalized = new StringBuilder();
        PlainTextComponentSerializer.plainText().serialize(label).toUpperCase(Locale.ROOT).codePoints().forEach(codepoint -> {
            if (Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)) {
                normalized.append(' ');
            } else {
                normalized.appendCodePoint(LABEL_CHARACTERS.indexOf(codepoint) >= 0 ? codepoint : '?');
            }
        });
        String text = normalized.toString();
        if (text.length() > maxLetters) {
            text = text.substring(0, maxLetters - 1) + "…";
        }
        return Component.text()
                .append(at(x, Component.text(tile.glyph()).font(Key.key("blockcraft", "tiles")), tile.shape().width() + 1))
                .append(at(labelX, Component.text(text).font(Key.key("blockcraft",
                        "label_" + tile.shape().name().toLowerCase(Locale.ROOT) + "_" + tile.slot() / 9)), text.length() * 4))
                .build();
    }

    private static Component at(int x, Component image, int width) {
        return Component.text().color(NamedTextColor.WHITE)
                .append(Component.text(shift(x - TITLE_X)).font(KEY))
                .append(image)
                .append(Component.text(shift(TITLE_X - x - width)).font(KEY)).build();
    }

    /** An icon in the colour of the text around it. */
    public static Component glyph(char glyph) {
        return Component.text(glyph).font(KEY);
    }

    static char panel(int rows, boolean toolbar) {
        if (rows < (toolbar ? 2 : 1) || rows > MAX_ROWS) {
            throw new IllegalArgumentException("No " + (toolbar ? "toolbar " : "") + "panel for " + rows + " rows");
        }
        return (char) ((toolbar ? TOOLBAR_PANEL_BASE : PANEL_BASE) + rows);
    }

    /** Moves the cursor by {@code pixels}, composed from the font's power-of-two spaces. */
    static String shift(int pixels) {
        char base = pixels < 0 ? SHIFT_LEFT_BASE : SHIFT_RIGHT_BASE;
        int remaining = Math.abs(pixels);
        var glyphs = new StringBuilder();
        for (int bit = 0; bit < SHIFT_BITS; bit++) {
            if ((remaining & (1 << bit)) != 0) {
                glyphs.append((char) (base + bit));
            }
        }
        return glyphs.toString();
    }
}
