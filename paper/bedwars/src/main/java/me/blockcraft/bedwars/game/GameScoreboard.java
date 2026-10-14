package me.blockcraft.bedwars.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.scoreboard.Sidebar;
import me.blockcraft.bedwars.team.Team;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;

/**
 * The sidebar: in the lobby who is there, the viewer's team and the map; during the round the map, the time left,
 * the viewer's kills and every team with its bed and its players; at the end the winner.
 */
public final class GameScoreboard {

    private static final int SECONDS_PER_MINUTE = 60;

    private final Game game;
    private final Messages messages;
    private final Sidebar sidebar;

    GameScoreboard(Game game, Messages messages) {
        this.game = game;
        this.messages = messages;
        this.sidebar = new Sidebar(messages.get("scoreboard.title"));
    }

    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    /** The headings carry icons, which only the viewer's client knows how to draw. */
    public void show(Player player) {
        var common = TagResolver.resolver(
                BlockcraftApi.get().resourcePack().icons(player),
                Placeholder.unparsed("map", game.mapName().orElse("")),
                Formatter.number("online", game.playerCount()),
                Formatter.number("max", game.capacity()));
        switch (game.phase()) {
            case LOBBY -> {
                var team = game.teams().of(player).map(chosen -> chosen.color().displayName(messages))
                        .orElseGet(() -> messages.get("scoreboard.no-team"));
                var map = game.mapName().isPresent() ? messages.get("scoreboard.map", common) : messages.get("scoreboard.voting");
                sidebar.show(player, messages.getLines("scoreboard.lobby", common,
                        Placeholder.component("team", team), Placeholder.component("map-line", map)));
            }
            case INGAME -> {
                var lines = new ArrayList<>(messages.getLines("scoreboard.round", common,
                        Placeholder.unparsed("time", clock(game.secondsLeft())),
                        Formatter.number("kills", game.kills(player))));
                game.teams().all().forEach(team -> lines.add(teamLine(team)));
                sidebar.show(player, lines);
            }
            case ENDING -> sidebar.show(player, game.winner()
                    .map(team -> messages.getLines("scoreboard.winner", common, Placeholder.component("team", team.color().displayName(messages))))
                    .orElseGet(() -> messages.getLines("scoreboard.draw", common)));
        }
    }

    private Component teamLine(Team team) {
        return messages.get(team.hasBed() ? "scoreboard.team-bed" : "scoreboard.team-no-bed",
                Placeholder.component("team", team.color().displayName(messages)),
                Formatter.number("players", team.size()));
    }

    private static String clock(int seconds) {
        return "%02d:%02d".formatted(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE);
    }
}
