package me.blockcraft.api.dialogue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

final class DialogueText {
    private static final Properties WIDTHS = loadWidths();

    private DialogueText() {}

    static int width(String text) {
        return text.codePoints().map(codepoint -> Integer.parseInt(WIDTHS.getProperty(Integer.toString(codepoint), "6"))).sum();
    }

    static String normalize(String text) {
        var result = new StringBuilder();
        text.codePoints().forEach(codepoint -> {
            if (codepoint == '\n') result.append('\n');
            else if (Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)) result.append(' ');
            else result.appendCodePoint(WIDTHS.containsKey(Integer.toString(codepoint)) ? codepoint : '?');
        });
        return result.toString();
    }

    static List<List<String>> pages(String text, int maxWidth) {
        var lines = new ArrayList<String>();
        for (String paragraph : normalize(text).split("\n", -1)) {
            var line = new StringBuilder();
            for (String word : paragraph.split(" +")) {
                if (word.isEmpty()) continue;
                if (!line.isEmpty() && width(line + " " + word) <= maxWidth) {
                    line.append(' ').append(word);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                for (int codepoint : word.codePoints().toArray()) {
                    String character = Character.toString(codepoint);
                    if (!line.isEmpty() && width(line + character) > maxWidth) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    line.append(character);
                }
            }
            lines.add(line.toString());
        }
        var pages = new ArrayList<List<String>>();
        for (int start = 0; start < lines.size(); start += 3) {
            pages.add(List.copyOf(lines.subList(start, Math.min(start + 3, lines.size()))));
        }
        return List.copyOf(pages);
    }

    static String fit(String text, int maxWidth) {
        String normalized = normalize(text).replace('\n', ' ');
        if (width(normalized) <= maxWidth) return normalized;
        var result = new StringBuilder();
        for (int codepoint : normalized.codePoints().toArray()) {
            if (width(result + Character.toString(codepoint) + "…") > maxWidth) break;
            result.appendCodePoint(codepoint);
        }
        return result.append('…').toString();
    }

    private static Properties loadWidths() {
        var widths = new Properties();
        try (var input = DialogueText.class.getResourceAsStream("/dialogue-glyphs.properties")) {
            if (input == null) throw new IllegalStateException("Missing generated dialogue glyph metrics");
            widths.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load dialogue glyph metrics", failure);
        }
        return widths;
    }
}
