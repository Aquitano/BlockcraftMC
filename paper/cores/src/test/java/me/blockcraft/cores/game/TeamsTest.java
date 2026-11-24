package me.blockcraft.cores.game;

import org.junit.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class TeamsTest {

    private static List<UUID> players(int count) {
        return Stream.generate(UUID::randomUUID).limit(count).toList();
    }

    @Test
    public void theRestFillsTheSmallerSide() {
        var teams = new Teams(4);
        var players = players(4);
        teams.join(players.get(0), Side.RED);
        teams.join(players.get(1), Side.RED);

        teams.assignRest(players);

        assertEquals(List.of(players.get(2), players.get(3)), teams.members(Side.BLUE));
        assertEquals(2, teams.size(Side.RED));
    }

    @Test
    public void aLoneSideGivesHalfToTheEmptyOne() {
        var teams = new Teams(4);
        var players = players(4);
        players.forEach(player -> teams.join(player, Side.BLUE));

        teams.assignRest(players);

        assertEquals(2, teams.size(Side.RED));
        assertEquals(List.of(players.get(2), players.get(3)), teams.members(Side.RED));
    }

    @Test
    public void aFullSideTakesNobodyElse() {
        var teams = new Teams(1);
        var players = players(2);
        teams.join(players.get(0), Side.RED);

        assertEquals(Teams.Joined.FULL, teams.join(players.get(1), Side.RED));
        assertEquals(Teams.Joined.ALREADY, teams.join(players.get(0), Side.RED));
    }
}
