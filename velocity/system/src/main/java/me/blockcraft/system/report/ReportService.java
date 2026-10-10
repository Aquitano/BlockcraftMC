package me.blockcraft.system.report;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.operation.NetworkRequest;
import me.blockcraft.common.network.operation.OperationResult;
import me.blockcraft.common.player.PlayerReference;
import me.blockcraft.common.report.ReportRepository;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerVisibility;
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

/**
 * Stores a report against the account behind the name the reporter sees, so a nick cannot be reported as a
 * player of its own and a vanished staff member cannot be found by name, then tells the staff who listen.
 */
public final class ReportService {

    private final ProxyServer proxy;
    private final Logger logger;
    private final ReportRepository reports;
    private final NotifyPreferences preferences;
    private final Messages messages;
    private final String staffPermission;
    private final Duration cooldown;
    private final Map<UUID, Instant> lastReport = new ConcurrentHashMap<>();

    private final PlayerLookup lookup;
    private final PlayerVisibility visibility;

    public ReportService(ProxyServer proxy, Logger logger, ReportRepository reports,
                         NotifyPreferences preferences, Messages messages, String staffPermission, Duration cooldown,
                         PlayerLookup lookup, PlayerVisibility visibility) {
        this.proxy = proxy;
        this.logger = logger;
        this.reports = reports;
        this.preferences = preferences;
        this.messages = messages;
        this.staffPermission = staffPermission;
        this.cooldown = cooldown;
        this.lookup = lookup;
        this.visibility = visibility;
    }

    public synchronized OperationResult submit(Player reporter, String targetName, String reason) {
        if (!PlayerReference.isName(targetName)) {
            reporter.sendMessage(messages.get("report.invalid-player"));
            return OperationResult.REJECTED;
        }
        if (!NetworkRequest.isText(reason)) {
            reporter.sendMessage(messages.get("report.reason-too-long", Formatter.number("max", NetworkRequest.MAX_TEXT_LENGTH)));
            return OperationResult.REJECTED;
        }
        var found = lookup.findVisible(reporter, targetName);
        if (found.isEmpty()) {
            reporter.sendMessage(messages.get("report.unknown-player"));
            return OperationResult.REJECTED;
        }
        var account = found.get();
        var target = account.online();
        String reported = account.name();
        String shown = target.map(player -> visibility.nameFor(reporter, player)).orElse(reported);
        if (reported.equalsIgnoreCase(reporter.getUsername())) {
            reporter.sendMessage(messages.get("report.self"));
            return OperationResult.REJECTED;
        }
        lastReport.entrySet().removeIf(entry -> !entry.getValue().plus(cooldown).isAfter(Instant.now()));
        var last = lastReport.get(reporter.getUniqueId());
        if (last != null) {
            long secondsLeft = Duration.between(Instant.now(), last.plus(cooldown)).toSeconds();
            if (secondsLeft > 0) {
                reporter.sendMessage(messages.get("report.cooldown", Formatter.number("seconds", secondsLeft)));
                return OperationResult.REJECTED;
            }
        }
        String server = target.flatMap(Player::getCurrentServer).map(ServerConnection::getServerInfo).map(ServerInfo::getName)
                .orElseGet(() -> messages.raw("report.offline-server"));
        int count;
        try {
            if (!reports.insert(PlayerReference.known(reporter.getUniqueId(), reporter.getUsername()),
                    PlayerReference.known(account.uuid(), account.name()), reason, server)) {
                reporter.sendMessage(messages.get("report.exists"));
                return OperationResult.REJECTED;
            }
            count = reports.countByReported(account.uuid(), account.name());
            lastReport.put(reporter.getUniqueId(), Instant.now());
        } catch (DatabaseException exception) {
            logger.error("Report of {} by {} failed", reported, reporter.getUsername(), exception);
            reporter.sendMessage(messages.get("report.error"));
            return OperationResult.FAILED;
        }
        notifyStaff(reporter, reported, reason, server, count);
        reporter.sendMessage(messages.get("report.success", Placeholder.unparsed("player", shown)));
        return OperationResult.APPLIED;
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
            if (!staff.hasPermission(staffPermission) || !preferences.enabled(staff)) {
                continue;
            }
            lines.forEach(staff::sendMessage);
        }
    }
}
