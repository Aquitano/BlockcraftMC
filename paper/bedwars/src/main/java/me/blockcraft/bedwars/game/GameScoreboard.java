package me.blockcraft.bedwars.game;

import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.game.Round;
import me.blockcraft.bedwars.team.Team;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The shared sidebar, with the viewer's team while the lobby waits, and during the round every team with its bed and
 * its players above the viewer's kills. The teams come first because the client shows no more than fifteen lines,
 * which the round's sidebar exceeds once it also tells the time ({@code timer-bar: false}).
 */
public final class GameScoreboard {

    private final Game game;
    private final Messages messages;
    private final GameSidebar sidebar;

    GameScoreboard(Game game, Messages messages, Round round) {
        this.game = game;
        this.messages = messages;
        this.sidebar = new GameSidebar(messages.get("scoreboard.title"), round);
    }

    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    public void show(Player player) {
        var icons = GameSidebar.icons(player);
        switch (game.phase()) {
            case LOBBY -> {
                var team = game.teams().of(player).map(chosen -> chosen.color().displayName(messages))
                        .orElseGet(() -> messages.get("scoreboard.no-team"));
                sidebar.lobby(player, game.capacity(), map(), List.of(
                        new GameSidebar.Section(messages.get("scoreboard.team", icons), GameSidebar.value(team))));
            }
            case INGAME -> sidebar.round(player, game.capacity(), game.secondsLeft(), map(), List.of(
                    new GameSidebar.Section(messages.get("scoreboard.teams", icons),
                            game.teams().all().stream().filter(team -> team.bed().isPresent())
                                    .map(this::teamLine).toList()),
                    new GameSidebar.Section(messages.get("scoreboard.kills", icons),
                            GameSidebar.value(Component.text(game.kills(player))))));
            case ENDING -> sidebar.end(player, game.winner().map(team -> messages.get("scoreboard.winner-team",
                    Placeholder.component("team", team.color().displayName(messages)))).stream().toList());
        }
    }

    private Component map() {
        return game.mapName().<Component>map(Component::text).orElseGet(() -> messages.get("scoreboard.voting"));
    }

    private Component teamLine(Team team) {
        return messages.get(team.hasBed() ? "scoreboard.team-bed" : "scoreboard.team-no-bed",
                Placeholder.component("team", team.color().displayName(messages)),
                Formatter.number("players", team.size()));
    }
}
