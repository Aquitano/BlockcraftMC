package me.blockcraft.quicksg.game;

import me.blockcraft.api.scoreboard.Sidebar;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.config.QuickSGConfig;
import me.blockcraft.quicksg.kit.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class GameScoreboard {

    private final Messages messages;
    private final QuickSGConfig config;
    private final Sidebar sidebar;

    public GameScoreboard(Messages messages, QuickSGConfig config) {
        this.messages = messages;
        this.config = config;
        this.sidebar = new Sidebar(messages.get("scoreboard.title"));
    }

    public void showLobby(Player player, int online, Kit kit) {
        sidebar.show(player, List.of(
                messages.get("scoreboard.online"),
                onlineLine(online),
                Component.empty(),
                messages.get("scoreboard.map"),
                messages.get("scoreboard.map-value", Placeholder.unparsed("map", config.mapName())),
                Component.empty(),
                messages.get("scoreboard.kit"),
                messages.get("scoreboard.kit-value", Placeholder.component("kit", messages.get(kit.messageKey() + ".name"))),
                Component.empty()));
    }

    public void showIngame(Player player, int online, int spectators) {
        sidebar.show(player, List.of(
                messages.get("scoreboard.online"),
                onlineLine(online),
                Component.empty(),
                messages.get("scoreboard.spectator"),
                messages.get("scoreboard.spectator-value", Formatter.number("spectators", spectators)),
                Component.empty(),
                messages.get("scoreboard.map"),
                messages.get("scoreboard.map-value", Placeholder.unparsed("map", config.mapName())),
                Component.empty()));
    }

    public void showEnding(Player player, List<String> winners) {
        var lines = new ArrayList<Component>();
        lines.add(messages.get("scoreboard.winner"));
        for (var winner : winners) {
            lines.add(messages.get("scoreboard.winner-value", Placeholder.unparsed("player", winner)));
        }
        sidebar.show(player, lines);
    }

    private Component onlineLine(int online) {
        return messages.get("scoreboard.online-value",
                Formatter.number("online", online), Formatter.number("max", config.maxPlayers()));
    }
}
