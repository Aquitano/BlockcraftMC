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
    public void oneSurvivorCanFinishTheFixedTeamTotal() {
        board.assign(List.of(innocent, detective), CHORES, 2, ANYWHERE, new Random(2));
        board.of(detective).forEach(duty -> assertTrue(board.finish(detective, duty.chore())));
        assertEquals(0.5f, board.progress(), 0f);
        assertFalse(board.complete());

        board.of(innocent).forEach(duty -> assertTrue(board.finish(detective, duty.chore())));
        assertEquals(1f, board.progress(), 0f);
        assertTrue(board.complete());
        assertEquals(4, board.contribution(detective));
    }

    @Test
    public void sharedWorkIsAvailableBeforeAnyoneDiesAndCreditsEachCompletionOnce() {
        board.assign(List.of(innocent, detective), CHORES, 1, ANYWHERE, new Random(2));
        var chore = board.of(innocent).getFirst().chore();
        int remaining = board.remaining(chore);
        assertTrue(board.available(detective).contains(chore));
        assertTrue(board.finish(detective, chore));
        assertEquals(remaining - 1, board.remaining(chore));
        assertEquals(0.5f, board.progress(), 0f);

        board.available(detective).forEach(each -> assertTrue(board.finish(detective, each)));
        assertFalse(board.finish(innocent, chore));
        assertEquals(2, board.contribution(detective));
        assertTrue(board.complete());
    }

    @Test
    public void traitorsCannotCompleteTasksAndAnEmptyBoardCannotWin() {
        assertFalse(board.complete());
        board.assign(List.of(innocent), CHORES, 1, ANYWHERE, new Random(2));
        var traitor = UUID.randomUUID();
        assertTrue(board.available(traitor).isEmpty());
        assertFalse(board.finish(traitor, board.of(innocent).getFirst().chore()));
        assertEquals(0f, board.progress(), 0f);
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
