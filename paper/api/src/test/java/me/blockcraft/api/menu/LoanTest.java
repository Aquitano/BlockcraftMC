package me.blockcraft.api.menu;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class LoanTest {

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
    };

    @Test
    public void givesTheOwnItemsBackWhereTheMenuStillShows() {
        var loan = new Loan<>(slots, item -> item.startsWith("menu"));
        loan.lend(0, "menu-map");
        loan.lend(1, "menu-card");
        loan.lend(1, "menu-card-chosen");

        loan.giveBack();

        assertArrayEquals(new String[] {null, "deploy", null}, inventory);
    }

    /** Deploying clears the inventory and hands out a loadout while the menu is still open. */
    @Test
    public void keepsWhatSomethingElseChangedAndWritesNoMoreOverIt() {
        var loan = new Loan<>(slots, item -> item.startsWith("menu"));
        loan.lend(0, "menu-map");
        loan.lend(1, "menu-blank");
        loan.lend(2, "menu-blank");
        Arrays.fill(inventory, null);
        inventory[0] = "blaster";
        loan.lend(0, "menu-map-again");

        loan.giveBack();

        assertArrayEquals(new String[] {"blaster", null, null}, inventory);
    }

    @Test
    public void anItemAnEarlierLoanLeftBehindIsNobodysOwn() {
        inventory[2] = "menu-left-by-a-crash";

        assertEquals(List.of("deploy"), new Loan<>(slots, item -> item.startsWith("menu")).own());
    }
}
