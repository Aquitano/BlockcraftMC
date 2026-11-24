package me.blockcraft.core.welcome;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Whether a chat line greets: one of the configured words standing alone. Both sides are evened out first, so case,
 * umlauts and their spellings and stretched letters do not matter: "GRÜSS", "gruß" and "gruess" all count as "grüß",
 * and "Hallooo" as "hallo".
 */
final class GreetingWords {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern REPEATED = Pattern.compile("(.)\\1+");

    private final Pattern greeting;

    GreetingWords(List<String> words) {
        String alternatives = words.stream().map(GreetingWords::normalised).filter(word -> !word.isBlank())
                .map(Pattern::quote).collect(Collectors.joining("|"));
        this.greeting = Pattern.compile(alternatives.isEmpty() ? "(?!)" : "(?<!\\w)(?:" + alternatives + ")(?!\\w)",
                Pattern.UNICODE_CHARACTER_CLASS);
    }

    boolean greets(String line) {
        return greeting.matcher(normalised(line)).find();
    }

    private static String normalised(String text) {
        String withoutMarks = MARKS.matcher(Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("");
        String unspelled = withoutMarks.replace("ß", "ss").replace("ae", "a").replace("oe", "o").replace("ue", "u");
        return REPEATED.matcher(unspelled).replaceAll("$1");
    }
}
