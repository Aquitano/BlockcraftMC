package me.blockcraft.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.chat.ChatFilter;
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

/**
 * Word and advertisement filter. Both drop the message and tell the staff on the whole network through the proxy;
 * advertisements are also logged for /werbung. The term list is loose on purpose (it catches obfuscated addresses),
 * so nothing is punished automatically; staff decide with /warn and /strafe.
 */
public final class ChatFilterListener implements Listener {

    private final Plugin plugin;
    private final ChatFilter filter;
    private final String serverName;
    private final AdLogRepository adLog;
    private final Messages messages;

    public ChatFilterListener(Plugin plugin, ChatFilter filter, String serverName, AdLogRepository adLog, Messages messages) {
        this.plugin = plugin;
        this.filter = filter;
        this.serverName = serverName;
        this.adLog = adLog;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (!player.hasPermission(ChatPermissions.WORDS_BYPASS) && filter.hasBlockedWord(text)) {
            event.setCancelled(true);
            player.sendMessage(messages.get("chat.word-filtered"));
            Tasks.sync(plugin, () -> Sounds.MENU_DENY.play(player));
            notifyStaff(player, text);
            return;
        }
        if (!player.hasPermission(ChatPermissions.ADS_BYPASS) && filter.isAdvertisement(text)) {
            event.setCancelled(true);
            notifyStaff(player, text);
            adLog.add(player.getUniqueId(), serverName, text);
            messages.getLines("chat.ad-filtered").forEach(player::sendMessage);
            Tasks.sync(plugin, () -> Sounds.MENU_DENY.play(player));
        }
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
