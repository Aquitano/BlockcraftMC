package me.blockcraft.lobby.activity;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class StarWishes implements Listener {

    private record Window(long until, Set<UUID> claimed) { }

    private final Messages messages;
    private final Pattern prefixes;
    private final Consumer<Player> discovery;
    private final LongSupplier clock;
    private volatile Window window;

    public StarWishes(Messages messages, List<String> prefixes, Consumer<Player> discovery, LongSupplier clock) {
        this.messages = messages;
        this.prefixes = Pattern.compile(prefixes.stream().map(Pattern::quote).collect(Collectors.joining("|", "^\\s*(?:", ")\\s+\\S")),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        this.discovery = discovery;
        this.clock = clock;
    }

    public void open(Duration duration) {
        window = new Window(clock.getAsLong() + duration.toNanos(), ConcurrentHashMap.newKeySet());
    }

    public void close() {
        window = null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        if (!prefixes.matcher(PlainTextComponentSerializer.plainText().serialize(event.message())).find()) {
            return;
        }
        event.setCancelled(true);
        event.viewers().clear();
        var current = window;
        var player = event.getPlayer();
        if (current == null || clock.getAsLong() >= current.until()) {
            player.sendMessage(messages.get("shooting-star.wish-late"));
        } else if (current.claimed().add(player.getUniqueId())) {
            player.sendMessage(messages.get("shooting-star.wish"));
            discovery.accept(player);
        }
    }
}
