package me.blockcraft.system.command.player;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.DurationText;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.moderation.MuteGate;
import me.blockcraft.system.support.SupportSessions;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.time.Duration;
import java.time.Instant;

public final class SupportCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final SupportSessions sessions;
    private final MuteGate muteGate;
    private final Messages messages;
    private final DurationText durations;

    public SupportCommand(CommandSupport support, ProxyServer proxy, SupportSessions sessions, MuteGate muteGate, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.sessions = sessions;
        this.muteGate = muteGate;
        this.messages = messages;
        this.durations = new DurationText(messages);
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("support")
                .executes(support.asPlayer((player, context) -> request(player)))
                .then(BrigadierCommand.literalArgumentBuilder("accept")
                        .requires(CommandSupport.permission(SystemPermissions.SUPPORT_STAFF))
                        .executes(support.usage("support.accept-usage"))
                        .then(support.playerArgument("player")
                                .executes(support.asPlayer((staff, context) -> accept(staff, StringArgumentType.getString(context, "player"))))))
                .then(BrigadierCommand.literalArgumentBuilder("list")
                        .requires(CommandSupport.permission(SystemPermissions.SUPPORT_STAFF))
                        .executes(support.run((source, context) -> list(source))))
                .then(BrigadierCommand.literalArgumentBuilder("close")
                        .requires(CommandSupport.permission(SystemPermissions.SUPPORT_STAFF))
                        .executes(support.usage("support.close-usage"))
                        .then(support.playerArgument("player")
                                .executes(support.asPlayer((staff, context) -> close(staff, StringArgumentType.getString(context, "player"))))))
                .then(BrigadierCommand.requiredArgumentBuilder("message", StringArgumentType.greedyString())
                        .executes(support.asPlayer((player, context) -> chat(player, StringArgumentType.getString(context, "message"))))));
    }

    private void request(Player player) {
        if (!sessions.open(player.getUniqueId())) {
            player.sendMessage(messages.get("support.already-open"));
            return;
        }
        messages.getLines("support.opened").forEach(player::sendMessage);
        var name = Placeholder.unparsed("player", player.getUsername());
        var lines = messages.getLines("support.staff-notify", name);
        var click = messages.get("support.staff-click", name)
                .clickEvent(ClickEvent.runCommand("/support accept " + player.getUsername()))
                .hoverEvent(HoverEvent.showText(messages.get("support.staff-click-hover", name)));
        var footer = messages.get("support.staff-footer");
        for (Player staff : proxy.getAllPlayers()) {
            if (staff.hasPermission(SystemPermissions.SUPPORT_STAFF)) {
                lines.forEach(staff::sendMessage);
                staff.sendMessage(click);
                staff.sendMessage(footer);
            }
        }
    }

    private void list(CommandSource source) {
        var open = sessions.open();
        if (open.isEmpty()) {
            source.sendMessage(messages.get("support.list-empty"));
            return;
        }
        source.sendMessage(messages.get("support.list-header", Formatter.number("count", open.size())));
        for (var request : open) {
            var requester = proxy.getPlayer(request.requester());
            if (requester.isEmpty()) {
                continue;
            }
            var name = Placeholder.unparsed("player", requester.get().getUsername());
            var waited = Placeholder.unparsed("waited", durations.format(Duration.between(request.openedAt(), Instant.now())));
            source.sendMessage(request.supporter().flatMap(proxy::getPlayer)
                    .map(supporter -> messages.get("support.list-assigned", name, waited,
                            Placeholder.unparsed("staff", supporter.getUsername())))
                    .orElseGet(() -> messages.get("support.list-waiting", name, waited)
                            .clickEvent(ClickEvent.runCommand("/support accept " + requester.get().getUsername()))
                            .hoverEvent(HoverEvent.showText(messages.get("support.staff-click-hover", name)))));
        }
    }

    private void accept(Player staff, String name) {
        var target = proxy.getPlayer(name);
        if (target.isEmpty()) {
            staff.sendMessage(messages.get("support.target-offline", Placeholder.unparsed("player", name)));
            return;
        }
        var player = target.get();
        if (sessions.assign(player.getUniqueId(), staff.getUniqueId())) {
            staff.sendMessage(messages.get("support.accepted", Placeholder.unparsed("player", player.getUsername())));
            player.sendMessage(messages.get("support.accepted-notify", Placeholder.unparsed("staff", staff.getUsername())));
        } else {
            staff.sendMessage(messages.get(sessions.state(player.getUniqueId()) == SupportSessions.State.NONE
                    ? "support.no-request" : "support.already-assigned", Placeholder.unparsed("player", player.getUsername())));
        }
    }

    private void close(Player staff, String name) {
        var target = proxy.getPlayer(name);
        if (target.isEmpty()) {
            staff.sendMessage(messages.get("support.target-offline", Placeholder.unparsed("player", name)));
            return;
        }
        var player = target.get();
        if (sessions.state(player.getUniqueId()) == SupportSessions.State.NONE) {
            staff.sendMessage(messages.get("support.no-request"));
            return;
        }
        if (!sessions.close(player.getUniqueId(), staff.getUniqueId())) {
            staff.sendMessage(messages.get("support.not-yours"));
            return;
        }
        staff.sendMessage(messages.get("support.closed", Placeholder.unparsed("player", player.getUsername())));
        player.sendMessage(messages.get("support.closed-notify", Placeholder.unparsed("staff", staff.getUsername())));
    }

    private void chat(Player player, String message) {
        switch (sessions.state(player.getUniqueId())) {
            case NONE -> player.sendMessage(messages.get("support.no-own-request"));
            case WAITING -> player.sendMessage(messages.get("support.wait"));
            case ASSIGNED -> {
                var supporter = sessions.supporter(player.getUniqueId()).flatMap(proxy::getPlayer);
                if (supporter.isEmpty()) {
                    player.sendMessage(messages.get("support.supporter-offline"));
                    sessions.close(player.getUniqueId());
                    return;
                }
                if (message.isBlank()) {
                    player.sendMessage(messages.get("support.empty-message"));
                    return;
                }
                if (!muteGate.allows(player)) {
                    return;
                }
                var line = messages.get("support.chat",
                        Placeholder.unparsed("player", player.getUsername()), Placeholder.unparsed("message", message));
                supporter.get().sendMessage(line);
                player.sendMessage(line);
            }
        }
    }
}
