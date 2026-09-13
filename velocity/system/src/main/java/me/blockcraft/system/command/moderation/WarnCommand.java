package me.blockcraft.system.command.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.WarnRepository;
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

import java.util.Optional;

/**
 * Warns an online player, or an offline one after confirming with the trailing "*" the click action appends.
 */
public final class WarnCommand {

    private static final String FORCE_SUFFIX = "*";

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final PlayerLookup lookup;
    private final WarnRepository warns;
    private final SystemConfig config;
    private final Messages messages;
    private volatile String lastForcedWarn = "";

    public WarnCommand(CommandSupport support, ProxyServer proxy, PlayerLookup lookup, WarnRepository warns, SystemConfig config,
                       Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.lookup = lookup;
        this.warns = warns;
        this.config = config;
        this.messages = messages;
    }

    /** "{@code <player>[*] [reason]}" is one greedy argument because Brigadier words cannot carry the "*". */
    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("warn")
                .requires(CommandSupport.permission(SystemPermissions.WARN))
                .executes(support.usage("warn.usage"))
                .then(BrigadierCommand.requiredArgumentBuilder("args", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            String typed = builder.getRemaining().toLowerCase();
                            if (!typed.contains(" ")) {
                                proxy.getAllPlayers().stream()
                                        .map(Player::getUsername)
                                        .filter(username -> username.toLowerCase().startsWith(typed))
                                        .forEach(builder::suggest);
                            }
                            return builder.buildFuture();
                        })
                        .executes(support.run((source, context) -> {
                            String[] parts = StringArgumentType.getString(context, "args").trim().split("\\s+", 2);
                            warn(source, parts[0], parts.length > 1 ? Optional.of(parts[1]) : Optional.empty());
                        }))));
    }

    private void warn(CommandSource source, String argument, Optional<String> reason) {
        boolean forced = argument.endsWith(FORCE_SUFFIX);
        String name = forced ? argument.substring(0, argument.length() - FORCE_SUFFIX.length()) : argument;
        var target = lookup.find(name);
        if (target.isEmpty()) {
            source.sendMessage(messages.get("player-unknown", Placeholder.unparsed("player", name)));
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
        if (!forced) {
            lastForcedWarn = "";
            source.sendMessage(messages.get("warn.offline"));
            var placeholders = Placeholder.unparsed("player", target.get().name());
            source.sendMessage(messages.get("warn.offline-click", placeholders)
                    .clickEvent(ClickEvent.runCommand("/warn " + target.get().name() + FORCE_SUFFIX))
                    .hoverEvent(HoverEvent.showText(messages.get("warn.offline-hover", placeholders))));
            return;
        }
        String key = CommandSupport.senderName(source) + " " + argument.toLowerCase();
        if (key.equals(lastForcedWarn)) {
            return;
        }
        lastForcedWarn = key;
        apply(source, target.get(), reason);
    }

    private void apply(CommandSource source, Target target, Optional<String> reason) {
        int count = warns.add(target.uuid(), reason);
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
}
