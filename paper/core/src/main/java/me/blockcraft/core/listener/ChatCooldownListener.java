package me.blockcraft.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.common.message.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One chat message per cooldown window, as the old proxy spam guard did. */
public final class ChatCooldownListener implements Listener {

    private final Duration cooldown;
    private final Messages messages;
    private final Map<UUID, Instant> lastMessage = new ConcurrentHashMap<>();

    public ChatCooldownListener(Duration cooldown, Messages messages) {
        this.cooldown = cooldown;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        if (player.hasPermission(ChatPermissions.SPAM_BYPASS)) {
            return;
        }
        var now = Instant.now();
        var last = lastMessage.put(player.getUniqueId(), now);
        if (last != null && Duration.between(last, now).compareTo(cooldown) < 0) {
            lastMessage.put(player.getUniqueId(), last);
            event.setCancelled(true);
            player.sendMessage(messages.get("chat.spam"));
        }
    }
}
