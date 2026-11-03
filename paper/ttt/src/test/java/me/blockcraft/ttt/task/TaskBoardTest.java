package me.blockcraft.ttt.task;

import me.blockcraft.ttt.arena.Chore;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskBoardTest {

    private static final List<Chore> CHORES = List.of(new Chore.Hold("well", null, false),
            new Chore.Puzzle("ledger", null, false), new Chore.Hold("bell", null, true));
    private static final BiPredicate<Chore, Chore> ANYWHERE = (one, other) -> false;

    private final UUID innocent = UUID.randomUUID();
    private final UUID detective = UUID.randomUUID();
    private final TaskBoard board = new TaskBoard();


    @Test
    public void aDeadInnocentsOpenChoresDropOutButTheirDoneOnesCount() {
        board.assign(List.of(innocent, detective), CHORES, 2, ANYWHERE, new Random(2));
        board.finish(board.of(innocent).getFirst());
        assertEquals(0.25f, board.progress(List.of(innocent, detective)), 0f);
        board.of(detective).forEach(board::finish);
        assertFalse(board.complete(List.of(innocent, detective)));
        assertTrue(board.complete(List.of(detective)));
    }

    @Test
    public void nobodyGetsTheSameChoreTwiceOrMoreThanTheMapHas() {
        board.assign(List.of(innocent), CHORES, 5, ANYWHERE, new Random(3));
        assertEquals(3, board.of(innocent).stream().map(TaskBoard.Duty::chore).distinct().count());
    }

    @Test
    public void aPlayersChoresStayApartWhileTheMapAllows() {
        BiPredicate<Chore, Chore> wellAndBellAreClose = (one, other) ->
                Set.of(one.key(), other.key()).equals(Set.of("well", "bell"));
        for (int seed = 0; seed < 20; seed++) {
            var board = new TaskBoard();
            board.assign(List.of(innocent), CHORES, 2, wellAndBellAreClose, new Random(seed));
            var keys = board.of(innocent).stream().map(duty -> duty.chore().key()).toList();
            assertTrue(keys.contains("ledger"));
        }
    }

    @Test
    public void aSpreadIsFoundEvenWhenTheFirstPickIsCloseToBoth() {
        BiPredicate<Chore, Chore> ledgerIsCloseToBoth = (one, other) ->
                Set.of(one.key(), other.key()).contains("ledger");
        for (int seed = 0; seed < 20; seed++) {
            var board = new TaskBoard();
            board.assign(List.of(innocent), CHORES, 2, ledgerIsCloseToBoth, new Random(seed));
            var keys = board.of(innocent).stream().map(duty -> duty.chore().key()).toList();
            assertEquals(Set.of("well", "bell"), Set.copyOf(keys));
        }
    }
}
