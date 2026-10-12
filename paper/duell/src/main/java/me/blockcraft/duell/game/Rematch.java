package me.blockcraft.duell.game;

import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The rematch a duel offers when it ends with both duellists still here. The first to ask is told the request is
 * out, and the opponent hears it with a button to accept; once both asked within the window, the proxy seats them
 * together in a fresh duel, or tells them there is no room.
 */
final class Rematch {

    /** The buttons stay clickable past the restart countdown, like the recap's. */
    private static final ClickCallback.Options BUTTON = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(1))
            .build();

    enum Answer { ASKED, WAITING, AGREED, SETTLED }

    /** Who asked when; the second request within the window of the first settles it, for good. */
    static final class Requests {

        private final Duration window;
        private final Map<UUID, Instant> asked = new HashMap<>();
        private boolean settled;

        Requests(Duration window) {
            this.window = window;
        }

        Answer ask(UUID player, UUID opponent, Instant now) {
            if (settled) {
                return Answer.SETTLED;
            }
            if (pending(opponent, now)) {
                settled = true;
                return Answer.AGREED;
            }
            if (pending(player, now)) {
                return Answer.WAITING;
            }
            asked.put(player, now);
            return Answer.ASKED;
        }

        private boolean pending(UUID player, Instant now) {
            var at = asked.get(player);
            return at != null && !now.isAfter(at.plus(window));
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Requests requests;

    Rematch(Plugin plugin, Messages messages, Duration window) {
        this.plugin = plugin;
        this.messages = messages;
        this.requests = new Requests(window);
    }

    void offer(Player player, Player opponent) {
        var opponentId = opponent.getUniqueId();
        player.sendMessage(button("rematch.offer", player.getUniqueId(), opponent.getName(), clicker -> ask(clicker, opponentId)));
    }

    private void ask(Player player, UUID opponentId) {
        var opponent = Bukkit.getPlayer(opponentId);
        if (opponent == null) {
            player.sendMessage(messages.get("rematch.gone"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        var opponentName = Placeholder.unparsed("opponent", opponent.getName());
        switch (requests.ask(player.getUniqueId(), opponentId, Instant.now())) {
            case ASKED -> {
                player.sendMessage(messages.get("rematch.asked", opponentName));
                var playerId = player.getUniqueId();
                opponent.sendMessage(messages.get("rematch.request", Placeholder.unparsed("player", player.getName()))
                        .append(button("rematch.accept", opponentId, player.getName(), clicker -> ask(clicker, playerId))));
                Sounds.NOTIFY.play(opponent);
            }
            case WAITING -> player.sendMessage(messages.get("rematch.waiting", opponentName));
            case AGREED -> ProxyMessaging.rematch(plugin, player, opponent);
            case SETTLED -> {
            }
        }
    }

    private Component button(String key, UUID recipient, String opponent, Consumer<Player> action) {
        var name = Placeholder.unparsed("opponent", opponent);
        return messages.get(key, name)
                .hoverEvent(HoverEvent.showText(messages.get(key + "-hover", name)))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player player && player.getUniqueId().equals(recipient)) {
                        Sounds.MENU_CLICK.play(player);
                        action.accept(player);
                    }
                }, BUTTON));
    }
}
