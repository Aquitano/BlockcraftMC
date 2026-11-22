package me.blockcraft.story.game;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class VoteTest {

    private final UUID leader = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID away = UUID.randomUUID();
    private final List<UUID> joined = List.of(away, leader, second);

    @Test
    public void waitsForEveryMemberWhoIsHere() {
        assertEquals(Optional.empty(), Vote.outcome(Map.of(leader, "fabrik"), List.of(leader, second), joined));
    }

    @Test
    public void aTieGoesToTheFirstJoinedMemberWhoIsHereNotOneWhoLeft() {
        var cast = Map.of(away, "gefangene", leader, "fabrik", second, "gefangene");
        assertEquals(Optional.of("fabrik"), Vote.outcome(cast, List.of(second, leader), joined));
    }
}
