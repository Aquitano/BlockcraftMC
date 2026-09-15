package me.blockcraft.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.AdLogRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.regex.Pattern;

/**
 * Word and advertisement filter. Both drop the message and tell the staff on the whole network through the proxy;
 * advertisements are also logged for /werbung. The term list is loose on purpose (it catches obfuscated addresses),
 * so nothing is punished automatically; staff decide with /warn and /strafe.
 */
public final class ChatFilterListener implements Listener {

    private static final Pattern URL = Pattern.compile(
            "(https?://)?(www\\.)?\\S{2,}\\.(com|ru|net|org|co\\.uk|fr|tk|info|me|es|de|arpa|edu|firm|int|mil|mobi|nato|to|ms|vu|nl|ch|us|dk)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IP = Pattern.compile(
            "(?<![0-9])((25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})[.,_ ]){3}(25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})(?![0-9])");

    private final Plugin plugin;
    private final ChatFilterConfig config;
    private final String serverName;
    private final AdLogRepository adLog;
    private final Messages messages;

    public ChatFilterListener(Plugin plugin, ChatFilterConfig config, String serverName, AdLogRepository adLog, Messages messages) {
        this.plugin = plugin;
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

    /** The notice carries its follow-ups: the three are proxy commands, so the staff's click runs them anywhere. */
    private void notifyStaff(Player player, String text) {
        var name = Placeholder.unparsed("player", player.getName());
        var notice = messages.get("chat.filtered-notify", name,
                        Placeholder.unparsed("message", text),
                        Placeholder.unparsed("server", serverName))
                .append(action("goto", name, ClickEvent.runCommand("/goto " + player.getName())))
                .append(action("tempmute", name, ClickEvent.suggestCommand("/tempmute " + player.getName() + " ")))
                .append(action("strafe", name, ClickEvent.suggestCommand("/strafe " + player.getName() + " ")));
        ProxyMessaging.notifyStaff(plugin, player, ChatPermissions.FILTER_NOTIFY, notice);
    }

    private Component action(String key, TagResolver name, ClickEvent click) {
        return Component.space().append(messages.get("chat.actions." + key)
                .clickEvent(click)
                .hoverEvent(HoverEvent.showText(messages.get("chat.actions." + key + "-hover", name))));
    }
}
