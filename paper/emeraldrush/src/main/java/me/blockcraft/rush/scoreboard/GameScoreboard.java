package me.blockcraft.rush.scoreboard;

import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.world.MapBundle;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.game.Game;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/** The shared sidebar, with the viewer's emeralds while shopping and everyone's lives in the deathmatch. */
public final class GameScoreboard {

    private final Game game;
    private final Messages messages;
    private final GameSidebar sidebar;
    private final Component map;
    private final int maxPlayers;

    public GameScoreboard(Game game, Messages messages, Round round, MapBundle map, int maxPlayers) {
        this.game = game;
        this.messages = messages;
        this.sidebar = new GameSidebar(messages.get("scoreboard.title"), round);
        this.map = Component.text(map.name());
        this.maxPlayers = maxPlayers;
    }

    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    public void show(Player player) {
        switch (game.phase()) {
            case LOBBY -> sidebar.lobby(player, maxPlayers, map, List.of());
            case SEARCH -> sidebar.round(player, maxPlayers, game.secondsLeft(), map, List.of());
            case SHOPPING -> sidebar.round(player, maxPlayers, game.secondsLeft(), map, List.of(emeralds(player)));
            case DEATHMATCH -> sidebar.round(player, maxPlayers, game.secondsLeft(), map, List.of(lives(player)));
            case ENDING -> sidebar.end(player, game.winnerNames().stream().<Component>map(Component::text).toList());
        }
    }

    private GameSidebar.Section emeralds(Player viewer) {
        return new GameSidebar.Section(messages.get("scoreboard.emeralds", GameSidebar.icons(viewer)),
                GameSidebar.value(Component.text(game.emeralds(viewer))));
    }

    private GameSidebar.Section lives(Player viewer) {
        return new GameSidebar.Section(messages.get("scoreboard.lives", GameSidebar.icons(viewer)),
                game.alivePlayers().stream()
                        .sorted(Comparator.comparingInt(game::lives).reversed())
                        .map(alive -> GameSidebar.value(messages.get("scoreboard.lives-value",
                                Placeholder.component("player", alive.playerListName()),
                                Formatter.number("lives", game.lives(alive)))))
                        .toList());
    }
}
