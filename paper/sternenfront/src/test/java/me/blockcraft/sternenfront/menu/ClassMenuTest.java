package me.blockcraft.sternenfront.menu;

import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ClassMenuTest {

    @Test
    public void everyRoleOfEveryFactionHasItsOwnTile() {
        long tiles = Arrays.stream(Faction.values())
                .flatMap(faction -> Arrays.stream(CombatClass.values()).map(combatClass -> ClassMenu.tileOf(faction, combatClass)))
                .distinct().count();

        assertEquals(Faction.values().length * CombatClass.values().length, tiles);
    }
}
