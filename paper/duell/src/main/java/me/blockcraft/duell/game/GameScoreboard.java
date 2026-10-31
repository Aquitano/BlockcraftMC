package me.blockcraft.duell.game;

import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.game.Round;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Optional;

public final class GameScoreboard {

    /** What every viewer's sidebar shares until the duel ends. */
    public record Board(Component mode, Component map, int secondsLeft, List<Component> tour) {
    }

    private final Messages messages;
    private final Round round;
    private final GameSidebar sidebar;

    public GameScoreboard(Messages messages, Round round) {
        this.messages = messages;
        this.round = round;
        this.sidebar = new GameSidebar(messages.get("scoreboard.title"), round);
    }

    public void showDuellist(Player viewer, Board board, Component rating, List<String> opponents, OptionalInt kills,
                             boolean sumo, Optional<Component> tourScore) {
        var icons = GameSidebar.icons(viewer);
        var sections = new ArrayList<GameSidebar.Section>();
        var mode = List.of(messages.get("scoreboard.mode-rating", Placeholder.component("mode", board.mode()),
                Placeholder.component("rating", rating)));
        sections.add(new GameSidebar.Section(messages.get("scoreboard.mode", icons), mode));
        sections.add(new GameSidebar.Section(messages.get("scoreboard.opponent", icons), opponents.isEmpty()
                ? List.of(GameSidebar.value(messages.get("scoreboard.opponent-none")))
                : opponents.stream().map(name -> GameSidebar.value(Component.text(name))).toList()));
        kills.ifPresent(count -> sections.add(new GameSidebar.Section(messages.get(sumo ? "scoreboard.points" : "scoreboard.kills", icons),
                GameSidebar.value(Component.text(count)))));
        if (!board.tour().isEmpty()) {
            var lines = new ArrayList<>(board.tour());
            tourScore.ifPresent(lines::add);
            sections.add(new GameSidebar.Section(messages.get("scoreboard.tour", icons), lines));
        }
        show(viewer, board, sections);
    }

    public void showSpectator(Player viewer, Board board, List<Component> duellists) {
        var icons = GameSidebar.icons(viewer);
        var sections = new ArrayList<GameSidebar.Section>();
        sections.add(new GameSidebar.Section(messages.get("scoreboard.mode", icons), GameSidebar.value(board.mode())));
        if (!duellists.isEmpty()) {
            sections.add(new GameSidebar.Section(messages.get("scoreboard.duellists", icons),
                    duellists.stream().map(GameSidebar::value).toList()));
        }
        if (!board.tour().isEmpty()) {
            sections.add(new GameSidebar.Section(messages.get("scoreboard.tour", icons), board.tour()));
        }
        show(viewer, board, sections);
    }

    public void showEnd(Player viewer, List<String> winners) {
        sidebar.end(viewer, winners.stream().<Component>map(Component::text).toList());
    }

    private void show(Player viewer, Board board, List<GameSidebar.Section> sections) {
        if (round.phase() == GamePhase.LOBBY) {
            sidebar.lobby(viewer, Duel.MAX_PARTICIPANTS, board.map(), sections);
        } else {
            sidebar.round(viewer, Duel.MAX_PARTICIPANTS, board.secondsLeft(), board.map(), sections);
        }
    }
}
