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
import me.blockcraft.common.message.Messages;
import me.blockcraft.report.storage.NotifyPreferences;
import me.blockcraft.common.report.ReportRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ReportCommand {

    private final Object plugin;
    private final ProxyServer proxy;
    private final PluginDescription description;
    private final ReportRepository reports;
    private final NotifyPreferences preferences;
    private final Messages messages;
    private final String staffPermission;
    private final Duration cooldown;
    private final Map<UUID, Instant> lastReport = new ConcurrentHashMap<>();

    public ReportCommand(Object plugin, ProxyServer proxy, PluginDescription description, ReportRepository reports,
                         NotifyPreferences preferences, Messages messages, String staffPermission, Duration cooldown) {
        this.plugin = plugin;
        this.proxy = proxy;
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
                        .executes(asPlayer((player, context) -> player.sendMessage(messages.get("usage"))))
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
        var target = proxy.getPlayer(targetName);
        String reported = target.map(Player::getUsername).orElse(targetName);
        if (reported.equalsIgnoreCase(reporter.getUsername())) {
            reporter.sendMessage(messages.get("report.self"));
            return;
        }
        if (reports.exists(reporter.getUsername(), reported)) {
            reporter.sendMessage(messages.get("report.exists"));
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
        String server = target.flatMap(Player::getCurrentServer).map(ServerConnection::getServerInfo).map(ServerInfo::getName)
                .orElseGet(() -> messages.raw("report.offline-server"));
        int previous = reports.countByReported(reported);
        notifyStaff(reporter, reported, reason, server, previous + 1);
        reports.insert(reporter.getUsername(), reported, reason, server);
        lastReport.put(reporter.getUniqueId(), Instant.now());
        reporter.sendMessage(messages.get("report.success", Placeholder.unparsed("player", reported)));
    }

    private void notifyStaff(Player reporter, String reported, String reason, String server, int count) {
        var placeholders = new TagResolver[]{
                Placeholder.unparsed("reporter", reporter.getUsername()),
                Placeholder.unparsed("reported", reported),
                Placeholder.unparsed("reason", reason),
                Placeholder.unparsed("server", server),
                Formatter.number("count", count)
        };
        for (Player staff : proxy.getAllPlayers()) {
            if (!staff.hasPermission(staffPermission) || preferences.isMuted(staff.getUniqueId())) {
                continue;
            }
            staff.sendMessage(messages.get("staff.header"));
            staff.sendMessage(messages.get("staff.reported", placeholders));
            staff.sendMessage(messages.get("staff.reason", placeholders));
            staff.sendMessage(messages.get("staff.server", placeholders));
            if (count > 1) {
                staff.sendMessage(messages.get("staff.count", placeholders));
            }
            staff.sendMessage(messages.get("staff.footer"));
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
