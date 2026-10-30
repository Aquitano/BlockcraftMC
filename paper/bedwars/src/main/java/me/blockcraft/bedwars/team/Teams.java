package me.blockcraft.bedwars.team;

import me.blockcraft.api.rank.TabTeams;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The round's teams and who is in which. A member's name takes the team's colour in chat, in the tab list and
 * above their head.
 */
public final class Teams {

    public enum Joined {
        JOINED,
        ALREADY,
        FULL
    }

    private static final int RIVALS = 2;

    private final Map<TeamColor, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, Team> byPlayer = new ConcurrentHashMap<>();
    private final int capacity;
    private final TabTeams tabTeams;

    public Teams(List<TeamColor> colors, int capacity, Messages messages, TabTeams tabTeams) {
        this.capacity = capacity;
        this.tabTeams = tabTeams;
        colors.forEach(color -> teams.put(color, new Team(color, capacity, messages.get("team-chest.title"))));
    }

    public Collection<Team> all() {
        return teams.values();
    }

    public Optional<Team> get(TeamColor color) {
        return Optional.ofNullable(teams.get(color));
    }

    public Optional<Team> of(Player player) {
        return Optional.ofNullable(byPlayer.get(player.getUniqueId()));
    }

    public Optional<TeamColor> colorOf(Player player) {
        return of(player).map(Team::color);
    }

    public boolean sameTeam(Player one, Player other) {
        var team = byPlayer.get(one.getUniqueId());
        return team != null && team == byPlayer.get(other.getUniqueId());
    }

    public Joined join(Player player, Team team) {
        if (team.isMember(player)) {
            return Joined.ALREADY;
        }
        if (team.isFull()) {
            return Joined.FULL;
        }
        of(player).ifPresent(old -> old.remove(player.getUniqueId()));
        team.add(player);
        byPlayer.put(player.getUniqueId(), team);
        wear(player, team);
        return Joined.JOINED;
    }

    /** A member who came back wears their team's colour again. */
    public void restore(Player player) {
        of(player).ifPresent(team -> wear(player, team));
    }

    private void wear(Player player, Team team) {
        tabTeams.color(player, team.color().text());
        player.displayName(Component.text(player.getName(), team.color().text()));
    }

    /** Takes the player out of their team; the team that is left empty is returned. */
    public Optional<Team> leave(Player player) {
        if (!byPlayer.containsKey(player.getUniqueId())) {
            return Optional.empty();
        }
        tabTeams.clearColor(player);
        player.displayName(null);
        return leave(player.getUniqueId());
    }

    /** As {@link #leave(Player)}, for a player who is gone. */
    public Optional<Team> leave(UUID player) {
        var team = byPlayer.remove(player);
        if (team == null) {
            return Optional.empty();
        }
        team.remove(player);
        return team.size() == 0 ? Optional.of(team) : Optional.empty();
    }

    /**
     * Seats everyone who chose no team. They fill as few teams as the player count needs, but at least two, so that
     * they still get teammates and the round has rivals; teams that already have members come first, and each player
     * joins the smallest team that has room.
     */
    public Map<Player, Team> assignRest(List<Player> players) {
        int wanted = Math.max(RIVALS, Math.ceilDiv(players.size(), capacity));
        var playing = new ArrayList<>(standing());
        teams.values().stream().filter(team -> team.size() == 0).limit(Math.max(0, wanted - playing.size()))
                .forEach(playing::add);
        var assigned = new LinkedHashMap<Player, Team>();
        for (var player : players) {
            if (of(player).isPresent()) {
                continue;
            }
            var team = playing.stream().filter(candidate -> !candidate.isFull()).min(Comparator.comparingInt(Team::size));
            if (team.isEmpty()) {
                break;
            }
            join(player, team.get());
            assigned.put(player, team.get());
        }
        return assigned;
    }

    /** If everyone chose the same team, its latest choosers move to an empty one, so that the round has rivals. */
    public Map<Player, Team> splitLoneTeam() {
        var moved = new LinkedHashMap<Player, Team>();
        var standing = standing();
        if (standing.size() != 1) {
            return moved;
        }
        var members = standing.getFirst().members();
        teams.values().stream().filter(team -> team.size() == 0).findFirst().ifPresent(rival ->
                members.subList(members.size() - members.size() / 2, members.size()).forEach(player -> {
                    join(player, rival);
                    moved.put(player, rival);
                }));
        return moved;
    }

    /** The teams someone still plays for. */
    public List<Team> standing() {
        return teams.values().stream().filter(team -> team.size() > 0).toList();
    }

    public int playerCount() {
        return byPlayer.size();
    }
}
