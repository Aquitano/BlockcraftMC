package me.blockcraft.api.pack;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * The glyphs of the resource pack's {@code blockcraft:ui} font, mirrored from {@code resourcepack/src/assets/blockcraft/font/ui.json}.
 * A menu panel is one glyph as tall as the whole chest screen, drawn in the title line and moved back to the
 * screen's corner with negative-width spaces; the title text then follows on top of it. The toolbar variant draws
 * three wide buttons instead of the first row of slots.
 */
public final class UiFont {

    public static final Key KEY = Key.key("blockcraft", "ui");
    /**
     * The vanilla letters from the pack's own copy, with the vanilla font behind them for anything else. Text whose width
     * the server counts draws in it, because a player's own pack may give {@code minecraft:font/ascii.png} other widths.
     */
    public static final Key TEXT = Key.key("blockcraft", "text");
    /** The letters German needs that the vanilla sheet lacks, which the pack draws beside its copy; DeployScreen spells with them. */
    public static final String TEXT_ACCENTS = "äöüÄÖÜß·…";
    /** The menu tiles' capitals, in the order the pack's label sheet draws them; Sternenfront's HUD fonts share them. */
    public static final String LABEL_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789ÄÖÜ-.:!?…/+&";
    /** A label letter's width with its spacing, the same in every font that draws them. */
    public static final int LABEL_ADVANCE = 4;

    /** A transparent picture as large as the 6-row panel, which a menu with a fixed layout draws on top of it. */
    public enum Overlay {
        STERNENFRONT_CLASS('\uE130'), STERNENFRONT_CLASS_WITH_BOTS('\uE131');

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
    /** Each letter's advance in {@link #TEXT}, which {@code resourcepack/PackArt.java} measures from the pack's sheets. */
    private static final Map<Integer, Integer> TEXT_ADVANCES = textAdvances();
    private static final int MAX_ROWS = 6;

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
        String text = capitals(PlainTextComponentSerializer.plainText().serialize(label), LABEL_LETTERS);
        if (text.length() > maxLetters) {
            text = text.substring(0, maxLetters - 1) + "…";
        }
        return Component.text()
                .append(at(x, Component.text(tile.glyph()).font(Key.key("blockcraft", "tiles")), tile.shape().width() + 1))
                .append(at(labelX, Component.text(text).font(Key.key("blockcraft",
                        "label_" + tile.shape().name().toLowerCase(Locale.ROOT) + "_" + tile.slot() / 9)), text.length() * LABEL_ADVANCE))
                .build();
    }

    /** {@code text} in capitals from {@code letters}: any space as a space, and a question mark for a letter they lack. */
    public static String capitals(String text, String letters) {
        var capitals = new StringBuilder();
        text.toUpperCase(Locale.ROOT).codePoints().forEach(codepoint -> {
            if (Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)) {
                capitals.append(' ');
            } else {
                capitals.appendCodePoint(letters.indexOf(codepoint) >= 0 ? codepoint : '?');
            }
        });
        return capitals.toString();
    }

    private static Component at(int x, Component image, int width) {
        return Component.text().color(NamedTextColor.WHITE)
                .append(Component.text(shift(x - TITLE_X)).font(KEY))
                .append(image)
                .append(Component.text(shift(TITLE_X - x - width)).font(KEY)).build();
    }

    /** {@code text} then empty space up to {@code width}, so what follows stays put however long {@code text} is. */
    public static Component leftAligned(Component text, int width) {
        return Component.empty().append(measured(text)).append(gap(width - width(text)));
    }

    /** Empty space up to {@code width}, then {@code text}, so what is before it stays put however long {@code text} is. */
    public static Component rightAligned(Component text, int width) {
        return Component.empty().append(gap(width - width(text))).append(measured(text));
    }

    /** {@code text} in {@link #TEXT}, the font whose widths {@link #width} counts, unless a part of it names its own. */
    public static Component measured(Component text) {
        return Component.text().font(TEXT).append(text).build();
    }

    private static Component gap(int pixels) {
        return Component.text(shift(Math.max(0, pixels))).font(KEY);
    }

    /** How wide the client draws {@code text} in {@link #TEXT}; a letter the pack lacks counts as an average one. */
    public static int width(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text).codePoints().map(UiFont::advance).sum();
    }

    private static int advance(int character) {
        return TEXT_ADVANCES.getOrDefault(character, UNKNOWN_ADVANCE);
    }

    private static Map<Integer, Integer> textAdvances() {
        var advances = new Properties();
        try (var input = UiFont.class.getResourceAsStream("/text-glyphs.properties")) {
            if (input == null) throw new IllegalStateException("Missing generated text glyph metrics");
            advances.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load text glyph metrics", failure);
        }
        return advances.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                entry -> Integer.parseInt((String) entry.getKey()), entry -> Integer.parseInt((String) entry.getValue())));
    }

    /** A 32-pixel picture centred on whatever text follows it, which it leaves where it was. */
    public static Component centredBehind(char glyph) {
        return Component.text(shift(-16) + glyph + shift(-17)).font(KEY);
    }

    /** {@code marker}, which takes no room, moved to the middle of {@code line} and followed by it, as a centred line is drawn. */
    public static Component centredOn(Component line, Component marker) {
        return centredOn(measured(line), width(line), marker);
    }

    /** As {@link #centredOn(Component, Component)} for a {@code line} with glyphs of the pack, whose width the caller knows. */
    public static Component centredOn(Component line, int width, Component marker) {
        int half = width / 2;
        return Component.empty().append(gap(half)).append(marker).append(Component.text(shift(-half)).font(KEY)).append(line);
    }

    /** {@code glyph} drawn over the {@code advance} pixels just written, which it must share to take no room. */
    public static Component over(char glyph, int advance) {
        return Component.text(shift(-advance) + glyph).font(KEY);
    }

    public static Component glyph(char glyph) {
        return Component.text(glyph).font(KEY);
    }

    /** Moves the cursor {@code pixels} to the right, or to the left when negative, without drawing anything. */
    public static Component space(int pixels) {
        return Component.text(shift(pixels)).font(KEY);
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
        int widest = 1 << (SHIFT_BITS - 1);
        while (remaining >= 2 * widest) {
            glyphs.append((char) (base + SHIFT_BITS - 1));
            remaining -= widest;
        }
        for (int bit = 0; bit < SHIFT_BITS; bit++) {
            if ((remaining & (1 << bit)) != 0) {
                glyphs.append((char) (base + bit));
            }
        }
        return glyphs.toString();
    }
}
