package me.blockcraft.story.dialogue;

import me.blockcraft.api.pack.UiFont;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * The dialogue panel above the hotbar as one action bar line. The pack's text shader moves every glyph whose colour
 * has red 234 to its row below the top of a space over the hotbar, as {@code hud_panel.glsl} describes, so the line
 * takes no width and the panel stays put however much it says. Letters draw from {@link UiFont#TEXT}, whose widths the
 * server counts even under a player's own font pack, and the panel's pieces from the pack's {@code blockcraft:story}.
 */
public final class DialoguePanel {

    /**
     * What the panel shows: the speaker, the part of the page revealed so far, the answers once the page is read with
     * the highlighted one, and the hint in the bottom-right corner. {@code rise} lowers the whole panel while it opens.
     */
    public record View(String speaker, List<String> lines, boolean more, List<String> answers, int selected, String hint,
                       int rise, boolean blink) {
        public View {
            lines = List.copyOf(lines);
            answers = List.copyOf(answers);
        }
    }

    /** The shader's tints, in the order of its tint table. */
    private enum Tint { WHITE, DIMMED, AQUA, RED, GOLD, SHADOW, YELLOW }

    private record Glyph(String text, Key font, int x, int y, Tint tint, int advance) { }

    static final Key FONT = Key.key("blockcraft", "story");
    public static final int TEXT_WIDTH = 282;
    public static final int MAX_LINES = 5;
    public static final int MAX_ANSWERS = 5;
    private static final int MARKER_RED = 234;
    private static final int FIRST_TINT = 176;
    /** The shader's space above the hotbar; a panel's rows count from its top, so a short panel starts lower. */
    private static final int AREA = 128;
    private static final int WIDTH = 300;
    private static final int LEFT = -WIDTH / 2;
    private static final int RIGHT = WIDTH / 2;
    private static final int TEXT_X = LEFT + 9;
    private static final int SPEAKER_Y = 6;
    private static final int BODY_Y = 18;
    private static final int LINE_PITCH = 10;
    private static final int ANSWER_PITCH = 12;
    private static final int ANSWER_TEXT_X = TEXT_X + 14;
    private static final int CAP = 8;
    private static final int[] SLICES = {64, 32, 16, 8};
    /** The wide pieces come as a left and a right half, the right one the next character, as resourcepack/StoryArt.java draws them. */
    private static final char TOP = '\uE000';
    private static final char BOTTOM = '\uE002';
    private static final char FIRST_SLICE = '\uE004';
    private static final char HIGHLIGHT = '\uE00C';
    private static final char BADGE = '\uE00E';
    private static final char BADGE_LIT = '\uE00F';
    private static final char SPEAKER = '\uE010';
    private static final char MORE = '\uE011';
    private static final int HIGHLIGHT_WIDTH = WIDTH - 12;
    private static final int BADGE_ADVANCE = 10;
    private static final int MARK_ADVANCE = 6;
    private static final int MORE_ADVANCE = 8;

    private DialoguePanel() {
    }

    /** {@code text} broken into lines no wider than the panel's text, in pages of {@link #MAX_LINES}. */
    public static List<List<String>> pages(String text) {
        var lines = new ArrayList<String>();
        for (var paragraph : text.split("\n")) {
            var line = new StringBuilder();
            for (var word : paragraph.trim().split(" +")) {
                if (word.isEmpty()) continue;
                if (!line.isEmpty() && width(line + " " + word) > TEXT_WIDTH) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(line.isEmpty() ? "" : " ").append(word);
            }
            lines.add(line.toString());
        }
        var pages = new ArrayList<List<String>>();
        for (int start = 0; start < lines.size(); start += MAX_LINES) {
            pages.add(List.copyOf(lines.subList(start, Math.min(lines.size(), start + MAX_LINES))));
        }
        return List.copyOf(pages);
    }

    /** The first {@code characters} of {@code page}, each line cut where the reveal has got to. */
    public static List<String> revealed(List<String> page, int characters) {
        var shown = new ArrayList<String>();
        int left = characters;
        for (var line : page) {
            if (left <= 0) break;
            int count = Math.min(left, line.length());
            shown.add(line.substring(0, count));
            left -= count;
        }
        return shown;
    }

    public static int length(List<String> page) {
        return page.stream().mapToInt(String::length).sum();
    }

    public static int width(String text) {
        return UiFont.width(Component.text(text));
    }

    /** The room a panel keeps for a whole page, so it does not grow while the page types out and its answers appear. */
    public record Size(int lines, int answers, boolean hint) { }

    /** How tall the panel is for a page of {@code lines} with {@code answers} and a hint, in whole slices. */
    static int height(int lines, int answers, boolean hint) {
        int bottom = BODY_Y + Math.max(1, lines) * LINE_PITCH + (answers > 0 ? 2 + answers * ANSWER_PITCH : 0) + (hint ? 12 : 2);
        return Math.min(AREA, Math.ceilDiv(bottom + 2, 8) * 8);
    }

    public static Component render(View view, Size size) {
        int pageLines = size.lines();
        int height = height(pageLines, size.answers(), size.hint());
        int top = AREA - height + view.rise();
        var glyphs = new ArrayList<Glyph>();
        wide(glyphs, TOP, LEFT, top, WIDTH);
        int y = CAP;
        while (y < height - CAP) {
            int left = height - CAP - y;
            for (int i = 0; i < SLICES.length; i++) {
                if (SLICES[i] <= left) {
                    wide(glyphs, (char) (FIRST_SLICE + 2 * (SLICES.length - 1 - i)), LEFT, top + y, WIDTH);
                    y += SLICES[i];
                    break;
                }
            }
        }
        wide(glyphs, BOTTOM, LEFT, top + height - CAP, WIDTH);
        glyphs.add(piece(SPEAKER, TEXT_X - 1, top + SPEAKER_Y, MARK_ADVANCE));
        glyphs.add(text(view.speaker(), TEXT_X + 7, top + SPEAKER_Y, Tint.AQUA));
        for (int i = 0; i < view.lines().size(); i++) {
            glyphs.add(text(view.lines().get(i), TEXT_X, top + BODY_Y + i * LINE_PITCH, Tint.WHITE));
        }
        int answersTop = top + BODY_Y + Math.max(1, pageLines) * LINE_PITCH + 3;
        for (int i = 0; i < view.answers().size(); i++) {
            int row = answersTop + i * ANSWER_PITCH;
            boolean selected = i == view.selected();
            if (selected) wide(glyphs, HIGHLIGHT, LEFT + 6, row - 1, HIGHLIGHT_WIDTH);
            glyphs.add(piece(selected ? BADGE_LIT : BADGE, TEXT_X, row - 1, BADGE_ADVANCE));
            glyphs.add(text(Integer.toString(i + 1), TEXT_X + 2, row, selected ? Tint.YELLOW : Tint.AQUA));
            glyphs.add(text(view.answers().get(i), ANSWER_TEXT_X, row, selected ? Tint.YELLOW : Tint.DIMMED));
        }
        if (!view.hint().isEmpty()) {
            int row = top + height - CAP - 8;
            int arrow = view.more() ? MORE_ADVANCE : 0;
            glyphs.add(text(view.hint(), RIGHT - 9 - arrow - width(view.hint()), row, Tint.DIMMED));
            if (view.more()) glyphs.add(piece(MORE, RIGHT - 8 - MORE_ADVANCE, row + (view.blink() ? 1 : 0), MORE_ADVANCE));
        }
        return line(glyphs);
    }

    private static void wide(List<Glyph> glyphs, char left, int x, int y, int width) {
        int half = width / 2;
        glyphs.add(piece(left, x, y, half + 1));
        glyphs.add(piece((char) (left + 1), x + half, y, width - half + 1));
    }

    private static Glyph piece(char glyph, int x, int y, int advance) {
        return new Glyph(Character.toString(glyph), FONT, x, y, Tint.WHITE, advance);
    }

    private static Glyph text(String text, int x, int y, Tint tint) {
        return new Glyph(text, UiFont.TEXT, x, y, tint, width(text));
    }

    /** The glyphs as one line that ends where it began, each glyph's colour naming its row and tint, without shadows. */
    private static Component line(List<Glyph> glyphs) {
        var line = Component.text();
        int cursor = 0;
        for (var glyph : glyphs) {
            if (glyph.text().isEmpty()) continue;
            if (glyph.x() != cursor) line.append(UiFont.space(glyph.x() - cursor));
            var colour = TextColor.color(MARKER_RED, Math.clamp(glyph.y(), 0, 255), FIRST_TINT + glyph.tint().ordinal());
            line.append(Component.text(glyph.text(), colour).font(glyph.font()).shadowColor(ShadowColor.none()));
            cursor = glyph.x() + glyph.advance();
        }
        if (cursor != 0) line.append(UiFont.space(-cursor));
        return line.build();
    }
}
