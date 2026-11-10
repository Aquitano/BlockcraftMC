package me.blockcraft.api.game;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A player killed in the round who calls the killer a cheater in chat soon after gets a button to report them, once
 * per kill; the message itself goes out as written. The button names the killer as everyone sees them, which is
 * the name {@code /report} resolves.
 */
final class Accusations implements Listener {

    private record Killing(UUID killer, Instant at) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Duration window;
    private final Pattern words;
    private final Map<UUID, Killing> killings = new ConcurrentHashMap<>();

    Accusations(Plugin plugin, Messages messages, Duration window, List<String> words) {
        this.plugin = plugin;
        this.messages = messages;
        this.window = window;
        this.words = pattern(words);
    }

    /** Matches any of {@code words} as a whole word in any case; without words, nothing. */
    static Pattern pattern(List<String> words) {
        if (words.isEmpty()) {
            return Pattern.compile("(?!)");
        }
        return Pattern.compile(words.stream().map(Pattern::quote).collect(Collectors.joining("|", "\\b(?:", ")\\b")),
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    }

    void killed(Player victim, Player killer) {
        killings.put(victim.getUniqueId(), new Killing(killer.getUniqueId(), Instant.now()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        var writer = event.getPlayer();
        var killing = killings.get(writer.getUniqueId());
        if (killing == null || Instant.now().isAfter(killing.at().plus(window))
                || !words.matcher(PlainTextComponentSerializer.plainText().serialize(event.message())).find()) {
            return;
        }
        Tasks.sync(plugin, () -> offer(writer, killing));
    }

    /** Runs a tick later, so the button follows the message it answers. */
    private void offer(Player writer, Killing killing) {
        var killer = Bukkit.getPlayer(killing.killer());
        if (killer == null || killer.equals(writer) || !writer.isOnline() || !killings.remove(writer.getUniqueId(), killing)) {
            return;
        }
        var name = Placeholder.unparsed("killer", killer.getName());
        writer.sendMessage(messages.get("hacker.report", name)
                .hoverEvent(HoverEvent.showText(messages.get("hacker.report-hover", name)))
                .clickEvent(ClickEvent.runCommand("/report " + killer.getName())));
    }
}
