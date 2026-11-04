package me.blockcraft.api.dialogue;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class DialogueView {
    private static final Key ART = Key.key("blockcraft", "dialogue");
    private static final TextColor WHITE = TextColor.color(0xFFFFFF);
    private static final TextColor INK = TextColor.color(0xDCE8F2);
    private static final TextColor ACCENT = TextColor.color(0x61DAD4);

    private DialogueView() {}

    static Component draw(DialogueSession session, Messages text, Messages messages) {
        var output = Component.text().color(WHITE)
                .append(at(0, Component.text('\uE600').font(ART), 301))
                .append(at(8, Component.text((char) (0xE610 + session.line().portrait().ordinal())).font(ART), 49));
        output.append(text(64, "speaker", plain(text, session.line().speaker()), 222, ACCENT));
        var lines = session.visibleLines();
        for (int index = 0; index < lines.size(); index++) {
            output.append(text(64, "body_" + index, lines.get(index), 222, INK));
        }
        if (session.choosing()) {
            for (int index = 0; index < session.line().choices().size(); index++) {
                String label = (index == session.choice() ? "> " : "  ")
                        + plain(text, session.line().choices().get(index).text());
                output.append(text(64, "choice_" + index, label, 222, index == session.choice() ? ACCENT : INK));
            }
        }
        String hint = plain(messages, session.choosing() ? "dialogue.controls-choices" : "dialogue.controls");
        output.append(text(8, "hint", hint, 284, TextColor.color(0x9BAEBD)));
        if (session.pageCount() > 1) {
            String page = PlainTextComponentSerializer.plainText().serialize(messages.get("dialogue.page",
                    Formatter.number("current", session.page() + 1), Formatter.number("total", session.pageCount())));
            output.append(text(8, "page", page, 48, INK));
        }
        return output.append(space(300)).build();
    }

    static String plain(Messages messages, String key) {
        return PlainTextComponentSerializer.plainText().serialize(messages.get(key));
    }

    private static Component text(int x, String font, String label, int width, TextColor color) {
        String fitted = DialogueText.fit(label, width);
        return at(x, Component.text(fitted).font(Key.key("blockcraft", "dialogue_" + font)).color(color),
                DialogueText.width(fitted));
    }

    private static Component at(int x, Component content, int width) {
        return space(x).append(content).append(space(-x - width));
    }

    private static Component space(int pixels) {
        var glyphs = new StringBuilder();
        int remaining = Math.abs(pixels);
        for (int bit = 0; bit < 10; bit++) {
            if ((remaining & (1 << bit)) != 0) glyphs.append((char) ((pixels < 0 ? 0xE640 : 0xE650) + bit));
        }
        return Component.text(glyphs.toString()).font(ART);
    }
}
