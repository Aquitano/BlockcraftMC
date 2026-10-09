package me.blockcraft.system.listener;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.WatchRepository;
import me.blockcraft.system.SystemPermissions;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Tells the team when a player with open reports or a punishment from the last {@code watch.recent-days} logs in, and
 * whoever may run {@code /alts} when the player shares an address with a banned account.
 */
public final class WatchListener {

    private final ProxyServer proxy;
    private final WatchRepository watches;
    private final Duration recent;
    private final Duration addressRetention;
    private final Messages messages;
    private final Logger logger;

    public WatchListener(ProxyServer proxy, WatchRepository watches, Duration recent, Duration addressRetention, Messages messages,
                         Logger logger) {
        this.proxy = proxy;
        this.watches = watches;
        this.recent = recent;
        this.addressRetention = addressRetention;
        this.messages = messages;
        this.logger = logger;
    }

    @Subscribe
    public EventTask onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        return EventTask.async(() -> {
            try {
                var watch = watches.of(player.getUniqueId(), player.getUsername(), recent, addressRetention);
                if (watch.hasRecord()) {
                    alert(player, watch);
                }
                if (watch.bannedAlts() > 0) {
                    alertBannedAlts(player, watch.bannedAlts());
                }
            } catch (DatabaseException e) {
                logger.warn("Could not look up reports and punishments of {}", player.getUsername(), e);
            }
        });
    }

    private void alert(Player player, WatchRepository.Watch watch) {
        var name = Placeholder.unparsed("player", player.getUsername());
        var notice = messages.get("watch.joined", name,
                        Formatter.number("reports", watch.openReports()),
                        Formatter.number("punishments", watch.recentPunishments()),
                        Formatter.number("days", recent.toDays()))
                .clickEvent(ClickEvent.runCommand("/whois " + player.getUsername()))
                .hoverEvent(HoverEvent.showText(messages.get("watch.hover", name)));
        for (Player staff : proxy.getAllPlayers()) {
            if (staff.hasPermission(SystemPermissions.TEAM)) {
                staff.sendMessage(notice);
            }
        }
    }

    private void alertBannedAlts(Player player, int bannedAlts) {
        var name = Placeholder.unparsed("player", player.getUsername());
        var notice = messages.get("watch.banned-alts", name, Formatter.number("count", bannedAlts))
                .clickEvent(ClickEvent.runCommand("/alts " + player.getUsername()))
                .hoverEvent(HoverEvent.showText(messages.get("watch.alts-hover", name)));
        for (Player staff : proxy.getAllPlayers()) {
            if (staff.hasPermission(SystemPermissions.ALTS)) {
                staff.sendMessage(notice);
            }
        }
    }
}
