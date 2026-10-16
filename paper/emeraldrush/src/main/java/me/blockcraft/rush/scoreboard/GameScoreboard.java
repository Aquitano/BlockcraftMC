package me.blockcraft.rush.scoreboard;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.scoreboard.Sidebar;
import me.blockcraft.api.world.MapBundle;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;

public final class GameScoreboard {

    private final Game game;
    private final Messages messages;
    private final MapBundle map;
    private final int maxPlayers;
    private final Sidebar sidebar;

    public GameScoreboard(Game game, Messages messages, MapBundle map, int maxPlayers) {
        this.game = game;
        this.messages = messages;
        this.map = map;
        this.maxPlayers = maxPlayers;
        this.sidebar = new Sidebar(messages.get("scoreboard.title"));
    }

    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    /** The headings carry icons, which only the viewer's client knows how to draw. */
    public void show(Player player) {
        var online = TagResolver.resolver(
                BlockcraftApi.get().resourcePack().icons(player),
                Formatter.number("online", game.alivePlayers().size()),
                Formatter.number("max", maxPlayers));
        switch (game.phase()) {
            case LOBBY -> sidebar.show(player, messages.getLines("scoreboard.lobby", online, Placeholder.unparsed("map", map.name())));
            case SEARCH -> sidebar.show(player, messages.getLines("scoreboard.search", online, Formatter.number("time", game.secondsLeft())));
            case SHOPPING -> sidebar.show(player, messages.getLines("scoreboard.shopping", online, Formatter.number("emeralds", game.emeralds(player))));
            case DEATHMATCH -> sidebar.showRows(player, game.alivePlayers().stream()
                    .map(alive -> new Sidebar.Row(alive.getName(), messages.get("scoreboard.lives",
                            Placeholder.component("player", alive.playerListName()),
                            Formatter.number("lives", game.lives(alive))), game.lives(alive)))
                    .toList());
            case ENDING -> {
                var names = game.winnerNames();
                var lines = new ArrayList<Component>();
                lines.add(messages.get(names.isEmpty() ? "scoreboard.draw" : "scoreboard.winner-header", online));
                names.forEach(winner -> lines.add(messages.get("scoreboard.winner", Placeholder.unparsed("player", winner))));
                sidebar.show(player, lines);
            }
        }
    }
}
