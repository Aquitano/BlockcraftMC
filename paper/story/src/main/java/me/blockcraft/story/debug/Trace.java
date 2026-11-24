package me.blockcraft.story.debug;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the story does, as one line per event: facts, stages, actions run, scenes, cues, timers, votes and
 * sounds. Each line goes to the server log as {@code [story] <kind> <text>}, into a short history for {@code /story
 * log}, and to the chat of whoever follows it with {@code /story trace}.
 */
public final class Trace {

    private static final int KEPT = 200;

    public record Event(long tick, String kind, String text) {
        public String line() {
            return "@" + tick + " " + kind + " " + text;
        }
    }

    private final Plugin plugin;
    private final Deque<Event> recent = new ArrayDeque<>();
    private final Set<UUID> following = new HashSet<>();

    public Trace(Plugin plugin) {
        this.plugin = plugin;
    }

    public void event(String kind, String text) {
        var event = new Event(Bukkit.getCurrentTick(), kind, text);
        recent.addLast(event);
        if (recent.size() > KEPT) recent.removeFirst();
        plugin.getSLF4JLogger().info("[story] {} {}", kind, text);
        var line = Component.text("[story] ", NamedTextColor.DARK_GRAY)
                .append(Component.text(kind + " ", NamedTextColor.GOLD))
                .append(Component.text(text, NamedTextColor.GRAY));
        following.stream().map(Bukkit::getPlayer).filter(player -> player != null && player.isOnline()).forEach(player -> player.sendMessage(line));
    }

    public boolean toggle(Player player) {
        if (following.remove(player.getUniqueId())) return false;
        following.add(player.getUniqueId());
        return true;
    }

    public List<Event> last(int count) {
        return recent.stream().skip(Math.max(0, recent.size() - count)).toList();
    }
}
