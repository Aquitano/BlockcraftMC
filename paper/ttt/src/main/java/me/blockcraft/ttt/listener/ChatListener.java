package me.blockcraft.ttt.listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.game.TttGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * A traitor's message that starts with {@link #TRAITOR_CHAT} reaches only the traitors, tagged as such; /tc writes
 * one. It is a chat message rather than a command's own line, so mutes and the chat filter still apply.
 */
public final class ChatListener implements Listener {

    public static final String TRAITOR_CHAT = "#";

    private final TttGame game;
    private final Messages messages;

    public ChatListener(TttGame game, Messages messages) {
        this.game = game;
        this.messages = messages;
    }

    /** Runs after core's chat format, whose line it marks rather than replaces. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var speaker = event.getPlayer();
        if (!PlainTextComponentSerializer.plainText().serialize(event.message()).startsWith(TRAITOR_CHAT)) {
            return;
        }
        var traitors = game.traitorChat();
        if (!traitors.contains(speaker.getUniqueId())) {
            return;
        }
        event.viewers().removeIf(viewer -> viewer instanceof Player reader && !traitors.contains(reader.getUniqueId()));
        event.message(event.message().replaceText(builder -> builder.matchLiteral(TRAITOR_CHAT).once().replacement("")));
        var tag = messages.get("chat.traitor").append(Component.space());
        var line = event.renderer();
        event.renderer((ChatRenderer) (source, displayName, message, viewer) -> tag.append(line.render(source, displayName, message, viewer)));
    }
}
