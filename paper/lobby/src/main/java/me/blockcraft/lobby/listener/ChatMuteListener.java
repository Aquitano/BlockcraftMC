package me.blockcraft.lobby.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.chat.ChatMute;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatMuteListener implements Listener {

    private final ChatMute chatMute;
    private final Messages messages;

    public ChatMuteListener(ChatMute chatMute, Messages messages) {
        this.chatMute = chatMute;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        if (chatMute.isMuted() && !event.getPlayer().hasPermission(LobbyPermissions.BYPASS)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("chat.muted"));
        }
    }
}
