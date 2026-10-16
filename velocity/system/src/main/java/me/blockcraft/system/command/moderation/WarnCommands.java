package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.Warn;
import me.blockcraft.common.moderation.WarnRepository;
import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerLookup.Target;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /warn} warns an online player, or an offline one after confirming with the trailing "*" the click action
 * appends; {@code /warns} counts, lists and clears them.
 */
public final class WarnCommands {

    private static final String FORCE_SUFFIX = "*";
    private static final int MAX_LISTED = 18;
    private static final String EVERY_PLAYER = "";

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final PlayerLookup lookup;
    private final WarnRepository warns;
    private final SystemConfig config;
    private final Messages messages;
    private final Map<PlayerReference, String> lastForcedWarn = new ConcurrentHashMap<>();
    private final Map<PlayerReference, String> pendingClears = new ConcurrentHashMap<>();

    public WarnCommands(CommandSupport support, ProxyServer proxy, PlayerLookup lookup, WarnRepository warns, SystemConfig config,
                        Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.lookup = lookup;
        this.warns = warns;
        this.config = config;
        this.messages = messages;
    }

    public List<BrigadierCommand> create() {
        return List.of(warn(), warns());
    }

    /** "{@code <player>[*] [reason]}" is one greedy argument because Brigadier words cannot carry the "*". */
    private BrigadierCommand warn() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("warn")
                .requires(CommandSupport.permission(SystemPermissions.WARN))
                .executes(support.usage("warn.usage"))
                .then(BrigadierCommand.requiredArgumentBuilder("args", StringArgumentType.greedyString())
                        .suggests((context, builder) -> builder.getRemaining().contains(" ")
                                ? builder.buildFuture()
                                : support.suggestPlayers(context.getSource(), builder))
                        .executes(support.run((source, context) -> {
                            String[] parts = StringArgumentType.getString(context, "args").trim().split("\\s+", 2);
                            warn(source, parts[0], parts.length > 1 ? Optional.of(parts[1]) : Optional.empty());
                        }))));
    }

    private BrigadierCommand warns() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("warns")
                .requires(CommandSupport.permission(SystemPermissions.WARNS))
                .executes(support.usage("warns.usage"))
                .then(confirmClear("clear"))
                .then(confirmClear("reset"))
                .then(BrigadierCommand.literalArgumentBuilder("clear*")
                        .executes(support.run((source, context) -> clearAll(source)))
                        .then(support.playerArgument("player")
                                .executes(support.run((source, context) -> clear(source, StringArgumentType.getString(context, "player"))))))
                .then(BrigadierCommand.literalArgumentBuilder("get")
                        .executes(support.usage("warns.usage"))
                        .then(support.playerArgument("player")
                                .executes(support.run((source, context) -> get(source, StringArgumentType.getString(context, "player"))))))
                .then(BrigadierCommand.literalArgumentBuilder("list")
                        .executes(support.run((source, context) -> list(source)))));
    }

    private void warn(CommandSource source, String argument, Optional<String> reason) {
        boolean forced = argument.endsWith(FORCE_SUFFIX);
        String name = forced ? argument.substring(0, argument.length() - FORCE_SUFFIX.length()) : argument;
        var target = lookup.find(source, name, messages.get("player-unknown", Placeholder.unparsed("player", name)));
        if (target.isEmpty()) {
            return;
        }
        if (!support.mayModerate(source, target.get().uuid())) {
            return;
        }
        if (target.get().online().isPresent()) {
            if (target.get().online().get().hasPermission(SystemPermissions.WARN)) {
                source.sendMessage(messages.get("warn.protected"));
                return;
            }
            apply(source, target.get(), reason);
            return;
        }
        var issuer = CommandSupport.sender(source);
        if (!forced) {
            lastForcedWarn.remove(issuer);
            source.sendMessage(messages.get("warn.offline"));
            var placeholders = Placeholder.unparsed("player", target.get().name());
            source.sendMessage(messages.get("warn.offline-click", placeholders)
                    .clickEvent(ClickEvent.runCommand("/warn " + target.get().name() + FORCE_SUFFIX))
                    .hoverEvent(HoverEvent.showText(messages.get("warn.offline-hover", placeholders))));
            return;
        }
        String forcedWarn = argument.toLowerCase();
        if (forcedWarn.equals(lastForcedWarn.put(issuer, forcedWarn))) {
            return;
        }
        apply(source, target.get(), reason);
    }

    private void apply(CommandSource source, Target target, Optional<String> reason) {
        int count = warns.add(target.uuid(), reason, CommandSupport.sender(source));
        var placeholders = TagResolver.resolver(
                Placeholder.unparsed("player", target.name()),
                Formatter.number("count", count),
                Formatter.number("max", config.maxWarns()),
                Placeholder.unparsed("reason", reason.orElse("")));
        proxy.sendMessage(messages.get(reason.isPresent() ? "warn.broadcast-reason" : "warn.broadcast", placeholders));
        boolean limitReached = count >= config.maxWarns();
        target.online().ifPresent(player -> {
            player.sendMessage(messages.get("warn.you-have", placeholders));
            if (limitReached) {
                player.disconnect(Component.join(JoinConfiguration.newlines(), messages.getLines("warn.kick-screen", placeholders)));
            }
        });
        if (limitReached) {
            messages.getLines("warn.ban-hint", placeholders).forEach(source::sendMessage);
            source.sendMessage(messages.get("warn.ban-click", placeholders)
                    .clickEvent(ClickEvent.runCommand("/strafe " + target.name()))
                    .hoverEvent(HoverEvent.showText(messages.get("warn.ban-hover", placeholders))));
        }
    }

    private LiteralArgumentBuilder<CommandSource> confirmClear(String literal) {
        return BrigadierCommand.literalArgumentBuilder(literal)
                .executes(support.run((source, context) -> askClearAll(source)))
                .then(support.playerArgument("player")
                        .executes(support.run((source, context) -> askClear(source, StringArgumentType.getString(context, "player")))));
    }

    private void askClearAll(CommandSource source) {
        pendingClears.put(CommandSupport.sender(source), EVERY_PLAYER);
        source.sendMessage(messages.get("warns.clear-all-confirm")
                .clickEvent(ClickEvent.runCommand("/warns clear*"))
                .hoverEvent(HoverEvent.showText(messages.get("warns.clear-hover"))));
    }

    private void askClear(CommandSource source, String player) {
        pendingClears.put(CommandSupport.sender(source), player.toLowerCase());
        source.sendMessage(messages.get("warns.clear-confirm", Placeholder.unparsed("player", player))
                .clickEvent(ClickEvent.runCommand("/warns clear* " + player))
                .hoverEvent(HoverEvent.showText(messages.get("warns.clear-hover"))));
    }

    /** {@code clear*} only follows a {@code clear} of the same players, so typing it straight away asks first. */
    private boolean confirmed(CommandSource source, String cleared) {
        return pendingClears.remove(CommandSupport.sender(source), cleared);
    }

    private void clearAll(CommandSource source) {
        if (!confirmed(source, EVERY_PLAYER)) {
            askClearAll(source);
            return;
        }
        warns.clearAll();
        source.sendMessage(messages.get("warns.cleared-all"));
    }

    private void clear(CommandSource source, String name) {
        if (!confirmed(source, name.toLowerCase())) {
            askClear(source, name);
            return;
        }
        lookup.find(source, name, messages.get("player-unknown", Placeholder.unparsed("player", name))).ifPresent(target -> {
            int removed = warns.clear(target.uuid());
            source.sendMessage(messages.get("warns.cleared", Placeholder.unparsed("player", target.name()), Formatter.number("count", removed)));
        });
    }

    private void get(CommandSource source, String name) {
        lookup.find(source, name, messages.get("player-unknown", Placeholder.unparsed("player", name))).ifPresent(target -> {
            var list = warns.list(target.uuid());
            source.sendMessage(messages.get("warns.get", Placeholder.unparsed("player", target.name()), Formatter.number("count", list.size())));
            for (int i = 0; i < list.size(); i++) {
                source.sendMessage(entry(i + 1, list.get(i)));
            }
        });
    }

    private Component entry(int number, Warn warn) {
        return messages.get("warns.entry",
                Formatter.number("number", number),
                Placeholder.component("reason", warn.reason().<Component>map(Component::text).orElseGet(() -> messages.get("warns.no-reason"))),
                Placeholder.component("issuer", warn.issuer().<Component>map(issuer -> Component.text(issuer.name()))
                        .orElseGet(() -> messages.get("warns.unknown-issuer"))));
    }

    private void list(CommandSource source) {
        var counts = warns.counts();
        if (counts.size() > MAX_LISTED) {
            source.sendMessage(messages.get("warns.too-many"));
            return;
        }
        source.sendMessage(messages.get("warns.list-header", Formatter.number("count", counts.size())));
        for (var entry : counts) {
            source.sendMessage(messages.get("warns.list-entry",
                    Placeholder.unparsed("player", entry.name()), Formatter.number("count", entry.count())));
        }
    }
}
