package me.blockcraft.api.dialogue;

import me.blockcraft.api.pack.HudPanel;
import me.blockcraft.api.pack.HudPanel.Corner;
import me.blockcraft.api.pack.HudPanel.Glyph;
import me.blockcraft.api.pack.HudPanel.Tint;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel standing on the hearts as a bottom-centre {@link HudPanel}: the portrait on the left, the speaker with the
 * controls beside them, then up to {@link #ROWS} rows of the page and its choices. It stays clear of the crosshair at the
 * smallest GUI the client picks, 240 pixels tall, which is why a page leaves room for its choices instead of growing.
 */
final class DialogueView {
    private static final Key ART = Key.key("blockcraft", "dialogue");
    private static final Key LETTERS = Key.key("blockcraft", "dialogue_text");
    static final int WIDTH = 300;
    static final int ROWS = 5;
    /** The panel for up to this many rows; a fifth takes the tall one. */
    static final int SHORT_ROWS = 4;
    static final int SHORT_HEIGHT = 56;
    static final int TALL_HEIGHT = 66;
    private static final int LEFT = -WIDTH / 2;
    private static final int PORTRAIT_X = LEFT + 4;
    private static final int PORTRAIT_Y = 4;
    private static final int PORTRAIT_ADVANCE = 49;
    private static final int TEXT_X = LEFT + 57;
    static final int TEXT_WIDTH = 239;
    private static final int HEADER_Y = 3;
    private static final int FIRST_ROW_Y = 14;
    private static final int ROW_PITCH = 10;
    private static final int HEADER_GAP = 6;
    private static final char SHORT_PANEL = '';
    private static final char TALL_PANEL = '';
    private static final char FIRST_PORTRAIT = '';

    private DialogueView() {}

    static int height(int rows) {
        return rows > SHORT_ROWS ? TALL_HEIGHT : SHORT_HEIGHT;
    }

    static Component draw(DialogueSession session, Messages text, Messages messages) {
        var lines = session.visibleLines();
        var choices = session.line().choices();
        boolean last = session.page() == session.pageCount() - 1;
        int rows = lines.size() + (last ? choices.size() : 0);
        int height = height(rows);
        int top = HudPanel.BOTTOM_CENTRE_AREA - height;
        char panel = height == TALL_HEIGHT ? TALL_PANEL : SHORT_PANEL;
        var art = List.of(
                new Glyph(Character.toString(panel), LEFT, top, Tint.WHITE, false, WIDTH / 2 + 1),
                new Glyph(Character.toString(panel + 1), 0, top, Tint.WHITE, false, WIDTH / 2 + 1),
                new Glyph(Character.toString(FIRST_PORTRAIT + session.line().portrait().ordinal()), PORTRAIT_X, top + PORTRAIT_Y,
                        Tint.WHITE, false, PORTRAIT_ADVANCE));

        var letters = new ArrayList<Glyph>();
        String speaker = DialogueText.fit(plain(text, session.line().speaker()), TEXT_WIDTH);
        letters.add(text(speaker, TEXT_X, top + HEADER_Y, Tint.AQUA));
        String hint = DialogueText.fit(plain(messages, session.choosing() ? "dialogue.controls-choices" : "dialogue.controls"),
                TEXT_WIDTH - DialogueText.width(speaker) - HEADER_GAP);
        letters.add(text(hint, TEXT_X + TEXT_WIDTH - DialogueText.width(hint), top + HEADER_Y, Tint.DIMMED));
        for (int index = 0; index < lines.size(); index++) {
            letters.add(text(lines.get(index), TEXT_X, row(top, index), Tint.WHITE));
        }
        if (session.choosing()) {
            for (int index = 0; index < choices.size(); index++) {
                boolean selected = index == session.choice();
                String label = DialogueText.fit((selected ? "> " : "  ") + plain(text, choices.get(index).text()), TEXT_WIDTH);
                letters.add(text(label, TEXT_X, row(top, lines.size() + index), selected ? Tint.AQUA : Tint.WHITE));
            }
        }
        if (session.pageCount() > 1 && rows < SHORT_ROWS) {
            String page = PlainTextComponentSerializer.plainText().serialize(messages.get("dialogue.page",
                    Formatter.number("current", session.page() + 1), Formatter.number("total", session.pageCount())));
            letters.add(text(page, TEXT_X + TEXT_WIDTH - DialogueText.width(page), row(top, SHORT_ROWS - 1), Tint.DIMMED));
        }
        return Component.textOfChildren(HudPanel.render(Corner.BOTTOM_CENTRE, ART, art),
                HudPanel.render(Corner.BOTTOM_CENTRE, LETTERS, letters));
    }

    static String plain(Messages messages, String key) {
        return PlainTextComponentSerializer.plainText().serialize(messages.get(key));
    }

    private static int row(int top, int index) {
        return top + FIRST_ROW_Y + index * ROW_PITCH;
    }

    private static Glyph text(String text, int x, int y, Tint tint) {
        return new Glyph(text, x, y, tint, false, DialogueText.width(text));
    }
}
