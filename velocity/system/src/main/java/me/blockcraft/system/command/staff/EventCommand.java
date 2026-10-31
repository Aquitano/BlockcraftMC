package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.calendar.ForcedOccasions;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.StaffActionRepository;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Switches the network's occasions without waiting for the date: {@code /event christmas winter} forces exactly those,
 * {@code /event none} forces none and {@code /event auto} lets the date decide again.
 */
public final class EventCommand {

    private static final String AUTO = "auto";
    private static final String NONE = "none";
    private static final String CONSOLE_SERVER = "proxy";

    private final CommandSupport support;
    private final NetworkCalendar calendar;
    private final ForcedOccasions forced;
    private final StaffActionRepository actions;
    private final Messages messages;
    private final Logger logger;

    public EventCommand(CommandSupport support, NetworkCalendar calendar, ForcedOccasions forced, StaffActionRepository actions,
                        Messages messages, Logger logger) {
        this.support = support;
        this.calendar = calendar;
        this.forced = forced;
        this.actions = actions;
        this.messages = messages;
        this.logger = logger;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("event")
                .requires(CommandSupport.permission(SystemPermissions.EVENT))
                .executes(support.run((source, context) -> status(source)))
                .then(BrigadierCommand.requiredArgumentBuilder("occasions", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggest(builder))
                        .executes(support.run((source, context) -> change(source, StringArgumentType.getString(context, "occasions"))))));
    }

    private void status(CommandSource source) {
        var active = occasions(calendar.occasions());
        source.sendMessage(calendar.forced().isPresent()
                ? messages.get("event.status-forced", active)
                : messages.get("event.status-auto", active));
    }

    private void change(CommandSource source, String typed) {
        var words = typed.toLowerCase(Locale.ROOT).strip().split("\\s+");
        Optional<Set<Occasion>> chosen;
        if (words.length == 1 && words[0].equals(AUTO)) {
            chosen = Optional.empty();
        } else if (words.length == 1 && words[0].equals(NONE)) {
            chosen = Optional.of(Set.of());
        } else {
            var occasions = EnumSet.noneOf(Occasion.class);
            for (String word : words) {
                var occasion = Arrays.stream(Occasion.values()).filter(candidate -> name(candidate).equals(word)).findFirst();
                if (occasion.isEmpty()) {
                    source.sendMessage(messages.get("event.unknown", Placeholder.unparsed("occasion", word),
                            occasions(EnumSet.allOf(Occasion.class))));
                    return;
                }
                occasions.add(occasion.get());
            }
            chosen = Optional.of(occasions);
        }
        try {
            forced.set(chosen);
        } catch (DatabaseException e) {
            logger.warn("Could not force the occasions {}", typed, e);
            source.sendMessage(messages.get("request-failed"));
            return;
        }
        String detail = chosen.map(occasions -> occasions.isEmpty() ? NONE : joined(occasions)).orElse(AUTO);
        audit(source, detail);
        status(source);
    }

    private void audit(CommandSource source, String detail) {
        try {
            actions.record(CommandSupport.sender(source), "event", Optional.empty(), Optional.of(detail),
                    source instanceof Player player ? CommandSupport.currentServer(player) : CONSOLE_SERVER);
        } catch (DatabaseException e) {
            logger.warn("Could not log the event switch to {}", detail, e);
        }
    }

    /** Completes the word being typed with the occasions not named yet; auto and none only stand alone. */
    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        int wordStart = remaining.lastIndexOf(' ') + 1;
        var named = List.of(remaining.substring(0, wordStart).split("\\s+"));
        String typed = remaining.substring(wordStart);
        var word = builder.createOffset(builder.getStart() + wordStart);
        var candidates = Arrays.stream(Occasion.values()).map(EventCommand::name).filter(name -> !named.contains(name));
        if (wordStart == 0) {
            candidates = Stream.concat(Stream.of(AUTO, NONE), candidates);
        }
        candidates.filter(name -> name.startsWith(typed)).forEach(word::suggest);
        return word.buildFuture();
    }

    private TagResolver occasions(Set<Occasion> occasions) {
        return occasions.isEmpty()
                ? Placeholder.component("occasions", messages.get("event.nothing"))
                : Placeholder.unparsed("occasions", joined(occasions));
    }

    private static String joined(Set<Occasion> occasions) {
        return occasions.stream().sorted().map(EventCommand::name).collect(Collectors.joining(", "));
    }

    private static String name(Occasion occasion) {
        return occasion.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
