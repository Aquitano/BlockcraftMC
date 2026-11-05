package me.blockcraft.sternenfront.bot;

import me.blockcraft.sternenfront.game.CombatClass;
import org.junit.Test;

import java.util.EnumSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class BotClassesTest {

    @Test
    public void eachFourSeatTeamDeploysEveryInfantryClass() {
        for (int first : new int[]{1, 5}) {
            var roles = IntStream.range(first, first + 4).mapToObj(Bots::infantryClass).collect(Collectors.toSet());
            assertEquals(EnumSet.allOf(CombatClass.class), roles);
        }
    }
}
