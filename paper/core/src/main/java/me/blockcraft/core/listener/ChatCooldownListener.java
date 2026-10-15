package me.blockcraft.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One chat message per cooldown window, as the old proxy spam guard did. While the proxy's /chatslow asks for a
 * longer window, that one applies.
 */
public final class ChatCooldownListener implements Listener {

    private final Plugin plugin;
    private final Duration cooldown;
    private final NetworkChat network;
    private final Messages messages;
    private final Map<UUID, Instant> lastMessage = new ConcurrentHashMap<>();

    public ChatCooldownListener(Plugin plugin, Duration cooldown, NetworkChat network, Messages messages) {
        this.plugin = plugin;
        this.cooldown = cooldown;
        this.network = network;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var player = event.getPlayer();
        if (player.hasPermission(ChatPermissions.SPAM_BYPASS)) {
            return;
        }
        var slowMode = network.slowMode();
        boolean slowed = slowMode.compareTo(cooldown) > 0;
        var window = slowed ? slowMode : cooldown;
        var now = Instant.now();
        var last = lastMessage.put(player.getUniqueId(), now);
        if (last != null && Duration.between(last, now).compareTo(window) < 0) {
            lastMessage.put(player.getUniqueId(), last);
            event.setCancelled(true);
            player.sendMessage(slowed
                    ? messages.get("chat.slow", Formatter.number("seconds", slowMode.toSeconds()))
                    : messages.get("chat.spam"));
            Tasks.sync(plugin, () -> Sounds.MENU_DENY.play(player));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessage.remove(event.getPlayer().getUniqueId());
    }
}
