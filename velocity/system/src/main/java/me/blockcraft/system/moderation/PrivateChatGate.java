package me.blockcraft.system.moderation;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.chat.ChatFilter;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.AdLogRepository;
import me.blockcraft.system.LiveSession;
import me.blockcraft.system.StaffButtons;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a private line passes before anyone reads it, for /msg, /r and party chat: one line per cooldown, the mute, and
 * the word and advertisement filter of public chat. A filtered line reaches only the staff, and an advertisement is
 * logged for /werbung. Filtered attempts count against the cooldown too, so nobody floods the staff with them.
 */
public final class PrivateChatGate {

    private final ProxyServer proxy;
    private final MuteGate mutes;
    private final ChatFilter filter;
    private final AdLogRepository adLog;
    private final StaffButtons buttons;
    private final Duration cooldown;
    private final Messages messages;
    private final Logger logger;
    private final Map<UUID, Instant> lastLine = new ConcurrentHashMap<>();

    public PrivateChatGate(ProxyServer proxy, MuteGate mutes, ChatFilter filter, AdLogRepository adLog, StaffButtons buttons,
                           Duration cooldown, Messages messages, Logger logger) {
        this.proxy = proxy;
        this.mutes = mutes;
        this.filter = filter;
        this.adLog = adLog;
        this.buttons = buttons;
        this.cooldown = cooldown;
        this.messages = messages;
        this.logger = logger;
    }

    /** True when the line may go out; otherwise the sender has been told why. */
    public boolean allows(Player sender, String text) {
        if (!sender.hasPermission(SystemPermissions.CHAT_SPAM_BYPASS)) {
            var now = Instant.now();
            var last = lastLine.put(sender.getUniqueId(), now);
            if (last != null && Duration.between(last, now).compareTo(cooldown) < 0) {
                lastLine.put(sender.getUniqueId(), last);
                sender.sendMessage(messages.get("private-chat.spam"));
                return false;
            }
        }
        if (!mutes.allows(sender)) {
            return false;
        }
        if (!sender.hasPermission(SystemPermissions.CHAT_WORDS_BYPASS) && filter.hasBlockedWord(text)) {
            sender.sendMessage(messages.get("private-chat.word-filtered"));
            notifyStaff(sender, text);
            return false;
        }
        if (!sender.hasPermission(SystemPermissions.CHAT_ADS_BYPASS) && filter.isAdvertisement(text)) {
            messages.getLines("private-chat.ad-filtered").forEach(sender::sendMessage);
            notifyStaff(sender, text);
            logAdvertisement(sender, text);
            return false;
        }
        return true;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (LiveSession.endedBy(event)) {
            lastLine.remove(event.getPlayer().getUniqueId());
        }
    }

    private void notifyStaff(Player sender, String text) {
        var notice = messages.get("private-chat.filtered-notify", Placeholder.unparsed("player", sender.getUsername()),
                        Placeholder.unparsed("message", text), Placeholder.unparsed("server", CommandSupport.currentServer(sender)))
                .append(buttons.of(sender.getUsername(), StaffButtons.Button.GOTO, StaffButtons.Button.WHOIS));
        proxy.getAllPlayers().stream().filter(staff -> staff.hasPermission(SystemPermissions.CHAT_FILTER_NOTIFY))
                .forEach(staff -> staff.sendMessage(notice));
    }

    private void logAdvertisement(Player sender, String text) {
        try {
            adLog.add(sender.getUniqueId(), CommandSupport.currentServer(sender), text);
        } catch (DatabaseException e) {
            logger.warn("Could not log an advertisement by {}", sender.getUsername(), e);
        }
    }
}
