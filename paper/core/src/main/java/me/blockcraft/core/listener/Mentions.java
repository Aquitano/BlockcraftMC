package me.blockcraft.core.listener;

import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/** A name written in a chat line as a whole word, in any case, with or without a leading {@code @}. */
final class Mentions {

    private Mentions() {
    }

    /** The line with every mention of {@code name} replaced by {@code highlight} of the text as written, if it has one. */
    static Optional<Component> highlight(Component message, String name, Function<String, Component> highlight) {
        var pattern = Pattern.compile("(?<![\\w@])@?" + Pattern.quote(name) + "(?!\\w)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
        var highlighted = message.replaceText(config -> config.match(pattern)
                .replacement((match, builder) -> highlight.apply(match.group())));
        return highlighted.equals(message) ? Optional.empty() : Optional.of(highlighted);
    }
}
