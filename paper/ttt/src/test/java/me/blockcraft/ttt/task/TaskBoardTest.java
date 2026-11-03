package me.blockcraft.ttt.task;

import me.blockcraft.ttt.arena.Chore;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskBoardTest {

    private static final List<Chore> CHORES = List.of(new Chore.Hold("well", null, false),
            new Chore.Puzzle("ledger", null, false), new Chore.Hold("bell", null, true));

    private final UUID innocent = UUID.randomUUID();
    private final UUID detective = UUID.randomUUID();
    private final UUID traitor = UUID.randomUUID();
    private final TaskBoard board = new TaskBoard();

    @Test
    public void aTraitorsChoresCountForNothing() {
        board.assign(List.of(innocent, traitor), Set.of(traitor), CHORES, 2, new Random(1));
        board.of(traitor).forEach(duty -> assertFalse(board.finish(traitor, duty)));
        assertEquals(0f, board.progress(List.of(innocent, traitor)), 0f);
        board.of(innocent).forEach(duty -> assertTrue(board.finish(innocent, duty)));
        assertTrue(board.complete(List.of(innocent, traitor)));
    }

    @Test
    public void aDeadInnocentsOpenChoresDropOutButTheirDoneOnesCount() {
        board.assign(List.of(innocent, detective), Set.of(), CHORES, 2, new Random(2));
        board.finish(innocent, board.of(innocent).getFirst());
        assertEquals(0.25f, board.progress(List.of(innocent, detective)), 0f);
        board.of(detective).forEach(duty -> board.finish(detective, duty));
        assertFalse(board.complete(List.of(innocent, detective)));
        assertTrue(board.complete(List.of(detective)));
    }

    @Test
    public void nobodyGetsTheSameChoreTwiceOrMoreThanTheMapHas() {
        board.assign(List.of(innocent), Set.of(), CHORES, 5, new Random(3));
        assertEquals(3, board.of(innocent).stream().map(TaskBoard.Duty::chore).distinct().count());
    }
}
