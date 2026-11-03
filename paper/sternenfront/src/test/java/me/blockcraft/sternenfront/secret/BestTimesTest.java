package me.blockcraft.sternenfront.secret;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class BestTimesTest {

    private static final UUID ANNA = UUID.randomUUID();
    private static final UUID BEN = UUID.randomUUID();
    private static final UUID CEM = UUID.randomUUID();

    @Test
    public void aRunnerShowsOnceWithTheirBestTime() {
        var board = new BestTimes(List.of(), 5)
                .with(new BestTimes.Entry(ANNA, "Anna", 200))
                .with(new BestTimes.Entry(BEN, "Ben", 180))
                .with(new BestTimes.Entry(ANNA, "Anna", 240))
                .with(new BestTimes.Entry(BEN, "Benno", 150));

        assertEquals(List.of(new BestTimes.Entry(BEN, "Benno", 150), new BestTimes.Entry(ANNA, "Anna", 200)), board.entries());
    }

    @Test
    public void onlyTheFastestFitOnTheBoard() {
        var board = new BestTimes(List.of(new BestTimes.Entry(ANNA, "Anna", 200), new BestTimes.Entry(BEN, "Ben", 220)), 2)
                .with(new BestTimes.Entry(CEM, "Cem", 210));

        assertEquals(List.of(ANNA, CEM), board.entries().stream().map(BestTimes.Entry::player).toList());
    }
}
