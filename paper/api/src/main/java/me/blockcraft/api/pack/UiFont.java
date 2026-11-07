package me.blockcraft.api.pack;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.map.MinecraftFont;

import java.util.Locale;

/**
 * The glyphs of the resource pack's {@code blockcraft:ui} font, mirrored from {@code resourcepack/src/assets/blockcraft/font/ui.json}.
 * A menu panel is one glyph as tall as the whole chest screen, drawn in the title line and moved back to the
 * screen's corner with negative-width spaces; the title text then follows on top of it. The toolbar variant draws
 * three wide buttons instead of the first row of slots.
 */
public final class UiFont {

    public static final Key KEY = Key.key("blockcraft", "ui");

    /** A transparent picture as large as the 6-row panel, which a menu with a fixed layout draws on top of it. */
    public enum Overlay {
        STERNENFRONT_CLASS(''), STERNENFRONT_CLASS_WITH_BOTS(''), STERNENFRONT_DEPLOY('');

        private final char glyph;

        Overlay(char glyph) {
            this.glyph = glyph;
        }

        char glyph() {
            return glyph;
        }
    }

    /** The chest screen is 176 pixels wide and its title starts 8 pixels in; a glyph is followed by one pixel of spacing. */
    private static final int PANEL_WIDTH = 176;
    private static final int TITLE_X = 8;
    private static final int GLYPH_SPACING = 1;
    private static final int UNKNOWN_ADVANCE = 6;
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
        return screenWide(cards ? (char) (CARD_PANEL_BASE + rows) : panel(rows, toolbar), title);
    }

    /** {@code overlay}, then {@code title}; a {@code Menu} draws the panel and its tiles before the title it was given. */
    public static Component overlaid(Overlay overlay, Component title) {
        return screenWide(overlay.glyph(), title);
    }

    private static Component screenWide(char glyph, Component title) {
        return Component.text()
                .append(Component.text(shift(-TITLE_X) + glyph + shift(TITLE_X - PANEL_WIDTH - GLYPH_SPACING))
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
    /** {@code text} then empty space up to {@code width}, so what follows stays put however long {@code text} is. */
    public static Component before(Component text, int width) {
        return Component.empty().append(text).append(gap(width - textWidth(text)));
    }

    /** Empty space up to {@code width}, then {@code text}, so what is before it stays put however long {@code text} is. */
    public static Component after(Component text, int width) {
        return Component.empty().append(gap(width - textWidth(text))).append(text);
    }

    private static Component gap(int pixels) {
        return Component.text(shift(Math.max(0, pixels))).font(KEY);
    }

    private static int textWidth(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text).codePoints().map(UiFont::advance).sum();
    }

    private static int advance(int character) {
        var text = Character.toString(character);
        return MinecraftFont.Font.isValid(text) ? MinecraftFont.Font.getWidth(text) + 1 : UNKNOWN_ADVANCE;
    }

    /** A 32-pixel picture centred on whatever text follows it, which it leaves where it was. */
    public static Component centredBehind(char glyph) {
        return Component.text(shift(-16) + glyph + shift(-17)).font(KEY);
    }

    /** {@code marker}, which takes no room, moved to the middle of {@code line} and followed by it, as a centred line is drawn. */
    public static Component centredOn(Component line, Component marker) {
        int half = textWidth(line) / 2;
        return Component.empty().append(gap(half)).append(marker).append(Component.text(shift(-half)).font(KEY)).append(line);
    }

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
