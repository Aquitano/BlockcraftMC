package me.blockcraft.common.chat;

import me.blockcraft.common.config.YamlFile;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Whether a chat line holds a blocked word or advertises another server, before anyone's bypass is considered. Public
 * chat on the backends and private chat on the proxy check the same lists, each from its plugin's
 * {@code chat-filter.yml}; the network's own addresses never count. The advertisement terms are loose on purpose to catch obfuscated addresses, so a term that
 * starts or ends with a letter only counts where no further letter is glued to that end: {@code .de} flags
 * "blockcraft.de" but not "ok.denke".
 */
public final class ChatFilter {

    private static final String FILE = "chat-filter.yml";
    private static final String NO_LETTER_AFTER = "(?!\\p{L})";
    private static final Pattern URL = Pattern.compile(
            "(https?://)?(www\\.)?\\S{2,}\\.(com|ru|net|org|co\\.uk|fr|tk|info|me|es|de|arpa|edu|firm|int|mil|mobi|nato|to|ms|vu|nl|ch|us|dk)"
                    + NO_LETTER_AFTER);
    private static final Pattern IP = Pattern.compile(
            "(?<![0-9])((25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})[.,_ ]){3}(25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})(?![0-9])");

    private static final Pattern SCHEME = Pattern.compile("^https?://");

    private final List<String> ownAddresses;
    private final List<String> blockedWords;
    private final List<Pattern> advertisementTerms;

    /** {@code ownAddresses} as configured under {@code network}; a website's scheme is not part of what players type. */
    public ChatFilter(Collection<String> ownAddresses, List<String> blockedWords, List<String> advertisementTerms) {
        this.ownAddresses = ownAddresses.stream().map(address -> SCHEME.matcher(address.toLowerCase(Locale.ROOT).strip()).replaceFirst(""))
                .filter(address -> !address.isEmpty()).sorted(Comparator.comparingInt(String::length).reversed()).toList();
        this.blockedWords = blockedWords.stream().map(word -> word.toLowerCase(Locale.ROOT)).toList();
        this.advertisementTerms = advertisementTerms.stream().map(term -> term.toLowerCase(Locale.ROOT))
                .filter(term -> !term.isEmpty()).map(ChatFilter::term).toList();
    }

    /** The plugin's {@code chat-filter.yml}, written from the bundled lists on the first start. */
    public static ChatFilter load(Path dataDirectory, Collection<String> ownAddresses) {
        return fromYaml(YamlFile.loadWithDefaults(dataDirectory.resolve(FILE), () -> ChatFilter.class.getResourceAsStream("/" + FILE)),
                ownAddresses);
    }

    static ChatFilter fromYaml(Map<String, Object> root, Collection<String> ownAddresses) {
        return new ChatFilter(ownAddresses, strings(root, "blocked-words"), strings(root, "advertisement-terms"));
    }

    public boolean hasBlockedWord(String text) {
        String lowercase = text.toLowerCase(Locale.ROOT);
        return blockedWords.stream().anyMatch(lowercase::contains);
    }

    public boolean isAdvertisement(String text) {
        String checked = withoutOwnAddresses(text.toLowerCase(Locale.ROOT));
        return URL.matcher(checked).find() || IP.matcher(checked).find()
                || advertisementTerms.stream().anyMatch(term -> term.matcher(checked).find());
    }

    private String withoutOwnAddresses(String text) {
        for (String address : ownAddresses) {
            text = text.replace(address, " ");
        }
        return text;
    }

    private static List<String> strings(Map<String, Object> root, String key) {
        return root.get(key) instanceof List<?> values ? values.stream().map(String::valueOf).toList() : List.of();
    }

    private static Pattern term(String term) {
        String start = Character.isLetter(term.codePointAt(0)) ? "(?<!\\p{L})" : "";
        String end = Character.isLetter(term.codePointBefore(term.length())) ? NO_LETTER_AFTER : "";
        return Pattern.compile(start + Pattern.quote(term) + end);
    }
}
