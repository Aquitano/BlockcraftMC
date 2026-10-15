package me.blockcraft.bedwars.team;

import me.blockcraft.api.rank.TabTeams;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

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

    private final Map<TeamColor, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, Team> byPlayer = new ConcurrentHashMap<>();
    private final TabTeams tabTeams;

    public Teams(List<TeamColor> colors, int capacity, Messages messages, TabTeams tabTeams) {
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
        of(player).ifPresent(old -> old.remove(player));
        team.add(player);
        byPlayer.put(player.getUniqueId(), team);
        tabTeams.color(player, team.color().text());
        player.displayName(Component.text(player.getName(), team.color().text()));
        return Joined.JOINED;
    }

    /** Takes the player out of their team; the team that is left empty is returned. */
    public Optional<Team> leave(Player player) {
        var team = byPlayer.remove(player.getUniqueId());
        if (team == null) {
            return Optional.empty();
        }
        team.remove(player);
        tabTeams.clearColor(player);
        player.displayName(null);
        return team.size() == 0 ? Optional.of(team) : Optional.empty();
    }

    /**
     * Seats everyone who chose no team, as the original did: the teams are ordered by size once, and each one is
     * filled before the next, so that the players who did not choose still play with teammates.
     */
    public Map<Player, Team> assignRest(List<Player> players) {
        var order = teams.values().stream().sorted(Comparator.comparingInt(Team::size)).toList();
        var assigned = new LinkedHashMap<Player, Team>();
        int index = 0;
        for (var player : players) {
            if (of(player).isPresent()) {
                continue;
            }
            while (index < order.size() && order.get(index).isFull()) {
                index++;
            }
            if (index == order.size()) {
                break;
            }
            join(player, order.get(index));
            assigned.put(player, order.get(index));
        }
        return assigned;
    }

    /** The teams someone still plays for. */
    public List<Team> standing() {
        return teams.values().stream().filter(team -> team.size() > 0).toList();
    }

    public int playerCount() {
        return byPlayer.size();
    }
}
