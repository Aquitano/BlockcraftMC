package me.blockcraft.api.game;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;

/**
 * While {@code running} holds a player on a team talks to their team; a message that starts with {@code @} goes to
 * everyone, tagged with the game's {@code chat.global}, and so does every message when the teams are of one.
 */
public final class TeamChat implements Listener {

    private static final String GLOBAL = "@";

    private final BooleanSupplier running;
    private final BiPredicate<Player, Player> sameTeam;
    private final Messages messages;
    private final boolean solo;

    /** {@code sameTeam} holds for a player and themselves exactly when they are on a team. */
    public TeamChat(BooleanSupplier running, BiPredicate<Player, Player> sameTeam, Messages messages, boolean solo) {
        this.running = running;
        this.sameTeam = sameTeam;
        this.messages = messages;
        this.solo = solo;
    }

    /** Runs after core's chat format, whose line it marks rather than replaces. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var speaker = event.getPlayer();
        if (!running.getAsBoolean() || !sameTeam.test(speaker, speaker)) {
            return;
        }
        boolean addressed = PlainTextComponentSerializer.plainText().serialize(event.message()).startsWith(GLOBAL);
        if (!addressed && !solo) {
            event.viewers().removeIf(viewer -> viewer instanceof Player reader && !sameTeam.test(speaker, reader));
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
