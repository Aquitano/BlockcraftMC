package me.blockcraft.api.game;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Prints the top ten of a stat with the module's {@code top.separator}, {@code top.first} and {@code top.entry} lines. */
public final class TopList {

    private static final int SIZE = 10;

    private final Plugin plugin;
    private final StatsRepository stats;
    private final Messages messages;

    public TopList(Plugin plugin, StatsRepository stats, Messages messages) {
        this.plugin = plugin;
        this.stats = stats;
        this.messages = messages;
    }

    public void send(Player player, Stat stat) {
        Tasks.async(plugin, () -> stats.top(stat, SIZE), entries -> {
            player.sendMessage(messages.get("top.separator"));
            int rank = 0;
            for (var entry : entries) {
                rank++;
                player.sendMessage(messages.get(rank == 1 ? "top.first" : "top.entry",
                        Formatter.number("rank", rank),
                        Placeholder.unparsed("name", String.valueOf(entry.name())),
                        Formatter.number("value", entry.value())));
            }
            player.sendMessage(messages.get("top.separator"));
        });
    }
}
