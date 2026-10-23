package me.blockcraft.bedwars.listener;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.bedwars.game.Game;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * During the round a player talks to their team; a message that starts with {@code @} goes to everyone, tagged as
 * such, and so does every message when the teams are of one.
 */
public final class ChatListener implements Listener {

    private static final String GLOBAL = "@";

    private final Game game;
    private final Messages messages;
    private final boolean solo;

    public ChatListener(Game game, Messages messages, boolean solo) {
        this.game = game;
        this.messages = messages;
        this.solo = solo;
    }

    /** Runs after core's chat format, whose line it marks rather than replaces. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (game.phase() != GamePhase.INGAME) {
            return;
        }
        var team = game.teams().of(event.getPlayer());
        if (team.isEmpty()) {
            return;
        }
        boolean addressed = PlainTextComponentSerializer.plainText().serialize(event.message()).startsWith(GLOBAL);
        if (!addressed && !solo) {
            event.viewers().removeIf(viewer -> viewer instanceof Player reader && !team.get().isMember(reader));
            return;
        }
        if (addressed) {
            event.message(event.message().replaceText(builder -> builder.matchLiteral(GLOBAL).once().replacement("")));
        }
        var tag = messages.get("chat.global").append(Component.space());
        var line = event.renderer();
        event.renderer((ChatRenderer) (source, displayName, message, viewer) ->
                tag.append(line.render(source, displayName, message, viewer)));
    }
}
