package me.blockcraft.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.AdLogRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.regex.Pattern;

/**
 * Word and advertisement filter. Both drop the message and tell the staff; advertisements are also logged for
 * /werbung. The term list is loose on purpose (it catches obfuscated addresses), so nothing is punished
 * automatically; staff decide with /warn and /strafe.
 */
public final class ChatFilterListener implements Listener {

    private static final Pattern URL = Pattern.compile(
            "(https?://)?(www\\.)?\\S{2,}\\.(com|ru|net|org|co\\.uk|fr|tk|info|me|es|de|arpa|edu|firm|int|mil|mobi|nato|to|ms|vu|nl|ch|us|dk)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IP = Pattern.compile(
            "(?<![0-9])((25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})[.,_ ]){3}(25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})(?![0-9])");

    private final ChatFilterConfig config;
    private final String serverName;
    private final AdLogRepository adLog;
    private final Messages messages;

    public ChatFilterListener(ChatFilterConfig config, String serverName, AdLogRepository adLog, Messages messages) {
        this.config = config;
        this.serverName = serverName;
        this.adLog = adLog;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        String lowercase = text.toLowerCase();
        if (!player.hasPermission(ChatPermissions.WORDS_BYPASS) && config.blockedWords().stream().anyMatch(lowercase::contains)) {
            event.setCancelled(true);
            player.sendMessage(messages.get("chat.word-filtered"));
            notifyStaff(player, text);
            return;
        }
        if (!player.hasPermission(ChatPermissions.ADS_BYPASS) && isAdvertisement(withoutAllowedDomain(lowercase))) {
            event.setCancelled(true);
            notifyStaff(player, text);
            adLog.add(player.getUniqueId(), serverName, text);
            messages.getLines("chat.ad-filtered").forEach(player::sendMessage);
        }
    }

    private String withoutAllowedDomain(String lowercase) {
        return config.allowedDomain().isEmpty() ? lowercase : lowercase.replace(config.allowedDomain(), " ");
    }

    private boolean isAdvertisement(String lowercase) {
        return URL.matcher(lowercase).find() || IP.matcher(lowercase).find()
                || config.advertisementTerms().stream().anyMatch(lowercase::contains);
    }

    private void notifyStaff(Player player, String text) {
        var notice = messages.get("chat.filtered-notify",
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("message", text),
                Placeholder.unparsed("server", serverName));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(ChatPermissions.FILTER_NOTIFY)) {
                staff.sendMessage(notice);
            }
        }
    }
}
