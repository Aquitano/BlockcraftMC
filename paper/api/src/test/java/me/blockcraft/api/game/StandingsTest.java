package me.blockcraft.api.game;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class StandingsTest {

    private static final UUID LISA = UUID.randomUUID();
    private static final UUID KEVOS = UUID.randomUUID();
    private static final UUID STEVE = UUID.randomUUID();

    @Test
    public void theOpponentFurthestAheadByTheLeadIsTheNemesis() {
        var standings = new Standings(List.of(
                new RivalryRepository.Rivalry(KEVOS, LISA, 5),
                new RivalryRepository.Rivalry(LISA, KEVOS, 1),
                new RivalryRepository.Rivalry(STEVE, LISA, 3)));

        assertEquals(Optional.of(KEVOS), standings.nemesis(LISA, List.of(LISA, KEVOS, STEVE), 3));
        assertEquals(new Standings.Standing(1, 5), standings.between(LISA, KEVOS));
        assertEquals(Optional.empty(), standings.nemesis(KEVOS, List.of(LISA, KEVOS, STEVE), 3));
    }

    @Test
    public void aLeadBelowTheThresholdOrAnOpponentElsewhereMakesNoNemesis() {
        var standings = new Standings(List.of(new RivalryRepository.Rivalry(KEVOS, LISA, 4), new RivalryRepository.Rivalry(LISA, KEVOS, 2)));

        assertEquals(Optional.empty(), standings.nemesis(LISA, List.of(LISA, KEVOS), 3));
        standings.count(KEVOS, LISA);
        assertEquals(Optional.of(KEVOS), standings.nemesis(LISA, List.of(LISA, KEVOS), 3));
        assertEquals(Optional.empty(), standings.nemesis(LISA, List.of(LISA, STEVE), 3));
    }
}
