package me.blockcraft.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.AdLogRepository;
import me.blockcraft.common.moderation.PunishmentRepository;
import me.blockcraft.common.moderation.WarnRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Word and advertisement filter. Blocked words only drop the message; advertisements also warn the player,
 * log the message for /werbung and ban at the warn limit.
 */
public final class ChatFilterListener implements Listener {

    private static final Pattern URL = Pattern.compile(
            "(https?://)?(www\\.)?\\S{2,}\\.(com|ru|net|org|co\\.uk|fr|tk|info|me|es|de|arpa|edu|firm|int|mil|mobi|nato|to|ms|vu|nl|ch|us|dk)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IP = Pattern.compile(
            "(?<![0-9])((25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})[.,_ ]){3}(25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})(?![0-9])");

    private final Plugin plugin;
    private final ChatFilterConfig config;
    private final WarnRepository warns;
    private final PunishmentRepository punishments;
    private final AdLogRepository adLog;
    private final Messages messages;

    public ChatFilterListener(Plugin plugin, ChatFilterConfig config, WarnRepository warns, PunishmentRepository punishments,
                              AdLogRepository adLog, Messages messages) {
        this.plugin = plugin;
        this.config = config;
        this.warns = warns;
        this.punishments = punishments;
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
            adLog.add(player.getUniqueId(), config.serverName(), text);
            messages.getLines("chat.ad-filtered").forEach(player::sendMessage);
            warn(player);
        }
    }

    private String withoutAllowedDomain(String lowercase) {
        return config.allowedDomain().isEmpty() ? lowercase : lowercase.replace(config.allowedDomain(), " ");
    }

    private boolean isAdvertisement(String lowercase) {
        return URL.matcher(lowercase).find() || IP.matcher(lowercase).find()
                || config.advertisementTerms().stream().anyMatch(lowercase::contains);
    }

    private void warn(Player player) {
        int count = warns.add(player.getUniqueId(), Optional.of(messages.raw("chat.ad-warn-reason")));
        var placeholders = TagResolver.resolver(Formatter.number("count", count), Formatter.number("max", config.maxWarns()));
        player.sendMessage(messages.get("chat.ad-warned", placeholders));
        if (count < config.maxWarns()) {
            return;
        }
        punishments.ban(player.getUniqueId(), messages.raw("chat.ad-ban-reason"), messages.raw("chat.ad-ban-issuer"), Optional.empty());
        var screen = Component.join(JoinConfiguration.newlines(), messages.getLines("chat.ad-ban-screen", placeholders));
        Bukkit.getScheduler().runTask(plugin, () -> player.kick(screen));
    }

    private void notifyStaff(Player player, String text) {
        var notice = messages.get("chat.filtered-notify",
                Placeholder.unparsed("player", player.getName()),
                Placeholder.unparsed("message", text),
                Placeholder.unparsed("server", config.serverName()));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(ChatPermissions.FILTER_NOTIFY)) {
                staff.sendMessage(notice);
            }
        }
    }
}
