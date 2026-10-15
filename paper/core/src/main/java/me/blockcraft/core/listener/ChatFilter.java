package me.blockcraft.core.listener;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whether a chat line holds a blocked word or advertises another server, before anyone's bypass is considered. The
 * advertisement terms are loose on purpose to catch obfuscated addresses, so a term that starts or ends with a
 * letter only counts where no further letter is glued to that end: {@code .de} flags "blockcraft.de" but not
 * "ok.denke".
 */
public final class ChatFilter {

    private static final String NO_LETTER_AFTER = "(?!\\p{L})";
    private static final Pattern URL = Pattern.compile(
            "(https?://)?(www\\.)?\\S{2,}\\.(com|ru|net|org|co\\.uk|fr|tk|info|me|es|de|arpa|edu|firm|int|mil|mobi|nato|to|ms|vu|nl|ch|us|dk)"
                    + NO_LETTER_AFTER);
    private static final Pattern IP = Pattern.compile(
            "(?<![0-9])((25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})[.,_ ]){3}(25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})(?![0-9])");

    private final String allowedDomain;
    private final List<String> blockedWords;
    private final List<Pattern> advertisementTerms;

    public ChatFilter(ChatFilterConfig config) {
        this.allowedDomain = config.allowedDomain();
        this.blockedWords = config.blockedWords();
        this.advertisementTerms = config.advertisementTerms().stream().filter(term -> !term.isEmpty()).map(ChatFilter::term).toList();
    }

    public boolean hasBlockedWord(String text) {
        String lowercase = text.toLowerCase(Locale.ROOT);
        return blockedWords.stream().anyMatch(lowercase::contains);
    }

    public boolean isAdvertisement(String text) {
        String lowercase = text.toLowerCase(Locale.ROOT);
        String checked = allowedDomain.isEmpty() ? lowercase : lowercase.replace(allowedDomain, " ");
        return URL.matcher(checked).find() || IP.matcher(checked).find()
                || advertisementTerms.stream().anyMatch(term -> term.matcher(checked).find());
    }

    private static Pattern term(String term) {
        String start = Character.isLetter(term.codePointAt(0)) ? "(?<!\\p{L})" : "";
        String end = Character.isLetter(term.codePointBefore(term.length())) ? NO_LETTER_AFTER : "";
        return Pattern.compile(start + Pattern.quote(term) + end);
    }
}
