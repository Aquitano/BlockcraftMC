package me.blockcraft.rush.scoreboard;

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
    private final Sidebar sidebar;

    public GameScoreboard(Game game, Messages messages, MapBundle map) {
        this.game = game;
        this.messages = messages;
        this.map = map;
        this.sidebar = new Sidebar(messages.get("scoreboard.title"));
    }

    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    public void show(Player player) {
        var online = TagResolver.resolver(
                Formatter.number("online", game.alivePlayers().size()),
                Formatter.number("max", Bukkit.getMaxPlayers()));
        switch (game.phase()) {
            case LOBBY -> sidebar.show(player, messages.getLines("scoreboard.lobby", online, Placeholder.unparsed("map", map.name())));
            case SEARCH -> sidebar.show(player, messages.getLines("scoreboard.search", online, Formatter.number("time", game.secondsLeft())));
            case SHOPPING -> sidebar.show(player, messages.getLines("scoreboard.shopping", online, Formatter.number("emeralds", game.emeralds(player))));
            case DEATHMATCH -> sidebar.showRows(player, game.alivePlayers().stream()
                    .map(alive -> new Sidebar.Row(alive.getName(), alive.playerListName(), game.lives(alive)))
                    .toList());
            case ENDING -> {
                var winners = new ArrayList<Component>();
                winners.add(messages.get("scoreboard.winner-header"));
                game.alivePlayers().forEach(winner ->
                        winners.add(messages.get("scoreboard.winner", Placeholder.component("player", winner.playerListName()))));
                sidebar.show(player, winners);
            }
        }
    }
}
