package me.blockcraft.api.game;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * F for the fallen: for a while after a death in the round, a chat message that is only "F" is held back and counted
 * for the latest death, once per player and never for the one who died. When the time is up the round hears who
 * paid their respects. It listens after every listener that mutes or filters, so a message nobody would have read
 * never counts.
 */
final class Respects implements Listener {

    private static final long TICKS_PER_SECOND = 20L;

    /** The respects one death collects. */
    static final class Tribute {

        private final UUID deceased;
        private final String deceasedName;
        private final Map<UUID, String> payers = new LinkedHashMap<>();

        Tribute(UUID deceased, String deceasedName) {
            this.deceased = deceased;
            this.deceasedName = deceasedName;
        }

        boolean isFor(UUID player) {
            return deceased.equals(player);
        }

        void pay(UUID payer, String payerName) {
            if (!isFor(payer)) {
                payers.putIfAbsent(payer, payerName);
            }
        }

        Optional<Component> line(Messages messages) {
            var deceased = Placeholder.unparsed("deceased", deceasedName);
            return switch (payers.size()) {
                case 0 -> Optional.empty();
                case 1 -> Optional.of(messages.get("respect.one",
                        Placeholder.unparsed("player", payers.values().iterator().next()), deceased));
                default -> Optional.of(messages.get("respect.many", Formatter.number("count", payers.size()), deceased));
            };
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final long windowTicks;
    private final Deque<Tribute> open = new ArrayDeque<>();
    private final Map<Tribute, Map<Audience, Tribute>> audiences = new LinkedHashMap<>();

    Respects(Plugin plugin, Messages messages, Duration window) {
        this.plugin = plugin;
        this.messages = messages;
        this.windowTicks = Math.max(1, window.toSeconds() * TICKS_PER_SECOND);
    }

    static boolean isRespect(String message) {
        return message.strip().equalsIgnoreCase("f");
    }

    synchronized void died(Player deceased) {
        var tribute = new Tribute(deceased.getUniqueId(), deceased.getName());
        open.addLast(tribute);
        Tasks.later(plugin, windowTicks, () -> close(tribute));
    }

    /** Says every tribute still collecting at once, because nobody would be here any more to hear it later. */
    void closeAll() {
        List<Tribute> closing;
        synchronized (this) {
            closing = List.copyOf(open);
        }
        closing.forEach(this::close);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!isRespect(PlainTextComponentSerializer.plainText().serialize(event.message()))) {
            return;
        }
        var player = event.getPlayer();
        synchronized (this) {
            var latest = open.peekLast();
            if (latest == null || latest.isFor(player.getUniqueId())) {
                return;
            }
            latest.pay(player.getUniqueId(), player.getName());
            var perViewer = audiences.computeIfAbsent(latest, ignored -> new LinkedHashMap<>());
            for (var viewer : event.viewers()) {
                perViewer.computeIfAbsent(viewer, ignored -> new Tribute(latest.deceased, latest.deceasedName))
                        .pay(player.getUniqueId(), player.getName());
            }
        }
        event.setCancelled(true);
    }

    private void close(Tribute tribute) {
        Map<Audience, Tribute> viewers;
        synchronized (this) {
            if (!open.remove(tribute)) {
                return;
            }
            viewers = audiences.remove(tribute);
        }
        if (viewers != null) {
            viewers.forEach((viewer, visible) -> visible.line(messages).ifPresent(viewer::sendMessage));
        }
    }
}
