package me.blockcraft.duell.game;

import me.blockcraft.api.scoreboard.Sidebar;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class GameScoreboard {

    private final Messages messages;
    private final Sidebar sidebar;

    public GameScoreboard(Messages messages) {
        this.messages = messages;
        this.sidebar = new Sidebar(messages.get("scoreboard.title"));
    }

    public void show(Player player, Component mode, Component map, List<String> opponents, OptionalInt kills) {
        var lines = new ArrayList<Component>();
        lines.add(Component.empty());
        lines.add(messages.get("scoreboard.mode"));
        lines.add(messages.get("scoreboard.mode-value", Placeholder.component("mode", mode)));
        lines.add(Component.empty());
        lines.add(messages.get("scoreboard.map"));
        lines.add(messages.get("scoreboard.map-value", Placeholder.component("map", map)));
        lines.add(Component.empty());
        lines.add(messages.get("scoreboard.opponent"));
        if (opponents.isEmpty()) {
            lines.add(messages.get("scoreboard.opponent-value", Placeholder.component("player", messages.get("scoreboard.opponent-none"))));
        } else {
            opponents.forEach(name -> lines.add(messages.get("scoreboard.opponent-value", Placeholder.unparsed("player", name))));
        }
        lines.add(Component.empty());
        if (kills.isPresent()) {
            lines.add(messages.get("scoreboard.kills"));
            lines.add(messages.get("scoreboard.kills-value", Formatter.number("kills", kills.getAsInt())));
        }
        sidebar.show(player, lines);
    }
}
