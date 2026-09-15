package me.blockcraft.report.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.report.storage.NotifyPreferences;
import me.blockcraft.common.report.ReportRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class ReportCommand {

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int MAX_REASON_LENGTH = 200;

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final PluginDescription description;
    private final ReportRepository reports;
    private final NotifyPreferences preferences;
    private final Messages messages;
    private final String staffPermission;
    private final Duration cooldown;
    private final Map<UUID, Instant> lastReport = new ConcurrentHashMap<>();

    public ReportCommand(Object plugin, ProxyServer proxy, Logger logger, PluginDescription description, ReportRepository reports,
                         NotifyPreferences preferences, Messages messages, String staffPermission, Duration cooldown) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.description = description;
        this.reports = reports;
        this.preferences = preferences;
        this.messages = messages;
        this.staffPermission = staffPermission;
        this.cooldown = cooldown;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("report")
                .executes(asPlayer((player, context) -> player.sendMessage(messages.get("usage"))))
                .then(BrigadierCommand.literalArgumentBuilder("info")
                        .executes(asPlayer((player, context) -> messages.getLines("info",
                                Placeholder.unparsed("author", String.join(", ", description.getAuthors())),
                                Placeholder.unparsed("version", description.getVersion().orElse("?"))).forEach(player::sendMessage))))
                .then(BrigadierCommand.literalArgumentBuilder("notify")
                        .executes(asStaff((player, context) -> player.sendMessage(
                                messages.get(preferences.isMuted(player.getUniqueId()) ? "notify.status-off" : "notify.status-on"))))
                        .then(BrigadierCommand.literalArgumentBuilder("on").executes(asStaff((player, context) -> toggle(player, true))))
                        .then(BrigadierCommand.literalArgumentBuilder("off").executes(asStaff((player, context) -> toggle(player, false))))
                        .then(BrigadierCommand.requiredArgumentBuilder("state", StringArgumentType.word())
                                .executes(asStaff((player, context) -> player.sendMessage(messages.get("notify.usage"))))))
                .then(BrigadierCommand.requiredArgumentBuilder("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            proxy.getAllPlayers().forEach(online -> builder.suggest(online.getUsername()));
                            return builder.buildFuture();
                        })
                        // Without a reason the backend opens its report dialog, which comes back here with one.
                        .executes(context -> context.getSource() instanceof Player ? BrigadierCommand.FORWARD
                                : asPlayer((player, ctx) -> player.sendMessage(messages.get("usage"))).run(context))
                        .then(BrigadierCommand.requiredArgumentBuilder("reason", StringArgumentType.greedyString())
                                .executes(asPlayer((player, context) -> proxy.getScheduler().buildTask(plugin, () -> report(player,
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason"))).schedule())))));
    }

    private void toggle(Player player, boolean enabled) {
        boolean muted = preferences.isMuted(player.getUniqueId());
        if (enabled == !muted) {
            player.sendMessage(messages.get(enabled ? "notify.already-enabled" : "notify.already-disabled"));
            return;
        }
        if (enabled) {
            preferences.unmute(player.getUniqueId());
        } else {
            preferences.mute(player.getUniqueId());
        }
        player.sendMessage(messages.get(enabled ? "notify.enabled" : "notify.disabled"));
    }

    private void report(Player reporter, String targetName, String reason) {
        if (!PLAYER_NAME.matcher(targetName).matches()) {
            reporter.sendMessage(messages.get("report.invalid-player"));
            return;
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            reporter.sendMessage(messages.get("report.reason-too-long", Formatter.number("max", MAX_REASON_LENGTH)));
            return;
        }
        var target = proxy.getPlayer(targetName);
        String reported = target.map(Player::getUsername).orElse(targetName);
        if (reported.equalsIgnoreCase(reporter.getUsername())) {
            reporter.sendMessage(messages.get("report.self"));
            return;
        }
        var last = lastReport.get(reporter.getUniqueId());
        if (last != null) {
            long secondsLeft = Duration.between(Instant.now(), last.plus(cooldown)).toSeconds();
            if (secondsLeft > 0) {
                reporter.sendMessage(messages.get("report.cooldown", Formatter.number("seconds", secondsLeft)));
                return;
            }
        }
        lastReport.put(reporter.getUniqueId(), Instant.now());
        String server = target.flatMap(Player::getCurrentServer).map(ServerConnection::getServerInfo).map(ServerInfo::getName)
                .orElseGet(() -> messages.raw("report.offline-server"));
        int count;
        try {
            if (reports.exists(reporter.getUsername(), reported)) {
                reporter.sendMessage(messages.get("report.exists"));
                return;
            }
            count = reports.countByReported(reported) + 1;
            reports.insert(reporter.getUsername(), reported, reason, server);
        } catch (DatabaseException exception) {
            logger.error("Report of {} by {} failed", reported, reporter.getUsername(), exception);
            reporter.sendMessage(messages.get("report.error"));
            return;
        }
        notifyStaff(reporter, reported, reason, server, count);
        reporter.sendMessage(messages.get("report.success", Placeholder.unparsed("player", reported)));
    }

    /** The reported name is the whole notice: it jumps to the player on click and carries the details on hover. */
    private void notifyStaff(Player reporter, String reported, String reason, String server, int count) {
        var details = TagResolver.resolver(
                Placeholder.unparsed("reporter", reporter.getUsername()),
                Placeholder.unparsed("reported", reported),
                Placeholder.unparsed("reason", reason),
                Placeholder.unparsed("server", server),
                Formatter.number("count", count));
        var target = messages.get("staff.target", details)
                .hoverEvent(HoverEvent.showText(Component.join(JoinConfiguration.newlines(), messages.getLines("staff.hover", details))))
                .clickEvent(ClickEvent.runCommand(PlainTextComponentSerializer.plainText()
                        .serialize(messages.get("staff.goto-command", details))));
        var lines = new ArrayList<Component>();
        lines.add(messages.get("staff.notice", TagResolver.resolver(Placeholder.component("target", target), details)));
        lines.add(messages.get("staff.reason", details));
        if (count > 1) {
            lines.add(messages.get("staff.count", details));
        }
        for (Player staff : proxy.getAllPlayers()) {
            if (!staff.hasPermission(staffPermission) || preferences.isMuted(staff.getUniqueId())) {
                continue;
            }
            lines.forEach(staff::sendMessage);
        }
    }

    private Command<CommandSource> asPlayer(PlayerAction action) {
        return context -> {
            if (!(context.getSource() instanceof Player player)) {
                context.getSource().sendMessage(messages.get("player-only"));
                return Command.SINGLE_SUCCESS;
            }
            action.run(player, context);
            return Command.SINGLE_SUCCESS;
        };
    }

    private Command<CommandSource> asStaff(PlayerAction action) {
        return asPlayer((player, context) -> {
            if (player.hasPermission(staffPermission)) {
                action.run(player, context);
            } else {
                player.sendMessage(messages.get("no-permission"));
            }
        });
    }

    @FunctionalInterface
    private interface PlayerAction {
        void run(Player player, CommandContext<CommandSource> context);
    }
}
