package me.blockcraft.core.listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.rank.Ranks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ChatFormatListener implements Listener {

    private final Messages messages;

    public ChatFormatListener(Messages messages) {
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) -> messages.get("chat.format",
                Placeholder.component("rank", Ranks.prefix(source).orElse(Component.empty())),
                Placeholder.component("name", displayName),
                Placeholder.component("message", message))));
    }
}
