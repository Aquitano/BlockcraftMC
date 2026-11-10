package me.blockcraft.api.menu;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class LoanTest {

    private static final String MARK = "menu:";
    private static final String FROM = "<";

    private final String[] inventory = {null, "deploy", null};
    private final Loan.Slots<String> slots = new Loan.Slots<>() {
        @Override
        public int size() {
            return inventory.length;
        }

        @Override
        public String get(int slot) {
            return inventory[slot];
        }

        @Override
        public void set(int slot, String item) {
            inventory[slot] = item;
        }

        @Override
        public String mark(String item, String original) {
            return MARK + item + FROM + (original == null ? "" : original);
        }

        @Override
        public boolean marked(String item) {
            return item.startsWith(MARK);
        }

        @Override
        public String original(String marked) {
            String original = marked.substring(marked.indexOf(FROM) + 1);
            return original.isEmpty() ? null : original;
        }
    };

    @Test
    public void givesTheOwnItemsBackWhereTheMenuStillShows() {
        var loan = new Loan<>(slots);
        loan.lend(0, "map");
        loan.lend(1, "card");
        loan.lend(1, "card-chosen");

        loan.giveBack();

        assertArrayEquals(new String[] {null, "deploy", null}, inventory);
    }

    /** Deploying clears the inventory and hands out a loadout while the menu is still open. */
    @Test
    public void keepsWhatSomethingElseChangedAndWritesNoMoreOverIt() {
        var loan = new Loan<>(slots);
        loan.lend(0, "map");
        loan.lend(1, "blank");
        loan.lend(2, "blank");
        Arrays.fill(inventory, null);
        inventory[0] = "blaster";
        loan.lend(0, "map-again");

        loan.giveBack();

        assertArrayEquals(new String[] {"blaster", null, null}, inventory);
    }

    /** A death drops what the player owns; an item a reset took away on purpose is no longer theirs. */
    @Test
    public void ownsOnlyWhatGivingBackWouldReturn() {
        inventory[2] = "medkit";
        var loan = new Loan<>(slots);
        loan.lend(1, "card");
        loan.lend(2, "blank");
        inventory[1] = "blaster";

        assertEquals(List.of("medkit"), loan.own());
    }

    /** The server saved the menu's items and crashed before the loan ended. */
    @Test
    public void aCrashThatSavedTheMenusItemsLosesNothing() {
        var loan = new Loan<>(slots);
        loan.lend(0, "map");
        loan.lend(1, "card");

        Loan.recover(slots);

        assertArrayEquals(new String[] {null, "deploy", null}, inventory);
    }

    @Test
    public void anItemAnEarlierLoanLeftBehindStandsForWhatItReplaced() {
        inventory[2] = slots.mark("map", "medkit");

        assertEquals(List.of("deploy", "medkit"), new Loan<>(slots).own());
    }
}
