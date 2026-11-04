package me.blockcraft.api.cosmetic;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One tag a player can wear, as the chat tag setting stores it: the tag's key, followed by the year for a yearly
 * tag ({@code neujahr-2027}). An earned choice is owned as the unlock {@code tag.<value>}.
 */
public record ChatTagChoice(ChatTag tag, OptionalInt year) {

    public static final String ITEM_PREFIX = "tag.";

    private static final Pattern YEARLY = Pattern.compile("([a-z0-9]+)-(\\d{4})");

    public static ChatTagChoice of(ChatTag tag) {
        return new ChatTagChoice(tag, OptionalInt.empty());
    }

    public static ChatTagChoice of(ChatTag tag, int year) {
        return new ChatTagChoice(tag, OptionalInt.of(year));
    }

    /** Empty for a value that names no tag, or a yearly tag without its year, or a year on a tag that has none. */
    public static Optional<ChatTagChoice> parse(String value) {
        var yearly = YEARLY.matcher(value);
        if (yearly.matches()) {
            return ChatTag.byKey(yearly.group(1)).filter(ChatTag::yearly)
                    .map(tag -> of(tag, Integer.parseInt(yearly.group(2))));
        }
        return ChatTag.byKey(value).filter(tag -> !tag.yearly()).map(ChatTagChoice::of);
    }

    /** What the player may wear: every tag that is not earned for a premium player, then the earned ones they own. */
    public static List<ChatTagChoice> available(boolean premium, Set<String> ownedItems) {
        var choices = new ArrayList<ChatTagChoice>();
        if (premium) {
            Arrays.stream(ChatTag.values()).filter(tag -> !tag.earned()).map(ChatTagChoice::of).forEach(choices::add);
        }
        ownedItems.stream()
                .filter(item -> item.startsWith(ITEM_PREFIX))
                .map(item -> parse(item.substring(ITEM_PREFIX.length())))
                .flatMap(Optional::stream)
                .filter(choice -> choice.tag().earned())
                .sorted(Comparator.comparing((ChatTagChoice choice) -> choice.tag().ordinal())
                        .thenComparing(choice -> choice.year().orElse(0)))
                .forEach(choices::add);
        return List.copyOf(choices);
    }

    public String value() {
        return year.isPresent() ? tag.key() + "-" + year.getAsInt() : tag.key();
    }

    public String item() {
        return ITEM_PREFIX + value();
    }

    /** {@code messages} is BlockcraftAPI's own, which holds every tag's text. */
    public Component text(Messages messages) {
        return messages.get(tag.messageKey(), Placeholder.unparsed("year", year.isPresent() ? String.valueOf(year.getAsInt()) : ""));
    }
}
