package me.blockcraft.ttt.game;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class RolesTest {

    private static final Roles ROLES = new Roles(0.25, 0.125, 6);

    private static List<UUID> players(int count) {
        return Stream.generate(UUID::randomUUID).limit(count).toList();
    }

    private static long count(List<UUID> players, Role role) {
        return ROLES.assign(players).values().stream().filter(each -> each == role).count();
    }

    @Test
    public void aSmallRoundHasOneTraitorAndNoDetective() {
        var players = players(4);

        assertEquals(1, count(players, Role.TRAITOR));
        assertEquals(0, count(players, Role.DETECTIVE));
        assertEquals(3, count(players, Role.INNOCENT));
    }

    @Test
    public void aFullRoundHasThreeTraitorsAndOneDetective() {
        var players = players(12);

        assertEquals(3, count(players, Role.TRAITOR));
        assertEquals(1, count(players, Role.DETECTIVE));
        assertEquals(8, count(players, Role.INNOCENT));
    }

    @Test
    public void aDetectiveJoinsFromTheMinimumOn() {
        assertEquals(0, ROLES.detectives(5));
        assertEquals(1, ROLES.detectives(6));
        assertEquals(2, ROLES.detectives(16));
    }

    @Test
    public void rolesFollowTheShuffledOrder() {
        var players = players(8);

        var roles = ROLES.assign(players);

        assertEquals(Role.TRAITOR, roles.get(players.get(0)));
        assertEquals(Role.TRAITOR, roles.get(players.get(1)));
        assertEquals(Role.DETECTIVE, roles.get(players.get(2)));
        assertEquals(Collections.nCopies(5, Role.INNOCENT), players.subList(3, 8).stream().map(roles::get).toList());
    }
}
