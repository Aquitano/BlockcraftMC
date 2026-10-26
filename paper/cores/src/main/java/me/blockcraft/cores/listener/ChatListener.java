package me.blockcraft.cores.listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.common.message.Messages;
import me.blockcraft.cores.game.CoresGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** During the round a player talks to their team; a message that starts with {@code @} goes to everyone, tagged as such. */
public final class ChatListener implements Listener {

    private static final String GLOBAL = "@";

    private final CoresGame game;
    private final Messages messages;

    public ChatListener(CoresGame game, Messages messages) {
        this.game = game;
        this.messages = messages;
    }

    /** Runs after core's chat format, whose line it marks rather than replaces. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (game.phase() != GamePhase.INGAME) {
            return;
        }
        var speaker = event.getPlayer();
        if (game.sideOf(speaker).isEmpty()) {
            return;
        }
        if (!PlainTextComponentSerializer.plainText().serialize(event.message()).startsWith(GLOBAL)) {
            event.viewers().removeIf(viewer -> viewer instanceof Player reader && !game.sameTeam(speaker, reader));
            return;
        }
        event.message(event.message().replaceText(builder -> builder.matchLiteral(GLOBAL).once().replacement("")));
        var tag = messages.get("chat.global").append(Component.space());
        var line = event.renderer();
        event.renderer((ChatRenderer) (source, displayName, message, viewer) -> tag.append(line.render(source, displayName, message, viewer)));
    }
}
