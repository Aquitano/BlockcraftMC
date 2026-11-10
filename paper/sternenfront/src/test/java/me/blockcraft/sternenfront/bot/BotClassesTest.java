package me.blockcraft.sternenfront.bot;

import me.blockcraft.sternenfront.game.CombatClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class BotClassesTest {

    @Test
    public void eachSideDealsTheClassesInTheSameOrderWhateverItsSize() {
        var fielded = new ArrayList<CombatClass>();
        for (int seat = 0; seat < 6; seat++) {
            fielded.add(Bots.infantryClass(fielded));
        }
        assertEquals(List.of(CombatClass.SOLDIER, CombatClass.HEAVY, CombatClass.MEDIC, CombatClass.SCOUT,
                CombatClass.SOLDIER, CombatClass.HEAVY), fielded);
    }

    @Test
    public void aSeatFreedByALeavingBotGoesToTheMissingClass() {
        assertEquals(CombatClass.HEAVY, Bots.infantryClass(List.of(CombatClass.SOLDIER, CombatClass.MEDIC, CombatClass.SCOUT)));
    }
}
