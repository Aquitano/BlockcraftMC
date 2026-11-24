package me.blockcraft.api.game.rush;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class RushLayoutTest {

    @Test
    public void readsItsOwnOrderAndFallsBackToTheDefaultForAnythingElse() {
        var layout = new RushLayout(4, 0, 8);

        assertEquals(layout, RushLayout.parse(Optional.of(layout.serialize())));
        assertEquals(RushLayout.DEFAULT, RushLayout.parse(Optional.empty()));
        assertEquals(RushLayout.DEFAULT, RushLayout.parse(Optional.of("4,0")));
        assertEquals(RushLayout.DEFAULT, RushLayout.parse(Optional.of("4,4,8")));
        assertEquals(RushLayout.DEFAULT, RushLayout.parse(Optional.of("4,0,9")));
        assertEquals(RushLayout.DEFAULT, RushLayout.parse(Optional.of("stick,blocks,pickaxe")));
    }

    @Test
    public void movingIntoATakenSlotSwapsTheTwoPieces() {
        var moved = RushLayout.DEFAULT.move(RushLayout.Item.BLOCKS, 8);
        assertEquals(new RushLayout(0, 8, 2), moved);

        assertEquals(new RushLayout(8, 0, 2), moved.move(RushLayout.Item.STICK, 8));
        assertEquals(Optional.of(RushLayout.Item.PICKAXE), moved.at(2));
        assertEquals(Optional.empty(), moved.at(1));
    }
}
