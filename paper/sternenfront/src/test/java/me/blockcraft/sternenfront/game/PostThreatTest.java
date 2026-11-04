package me.blockcraft.sternenfront.game;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PostThreatTest {

    @Test
    public void alertsOnEntryWithoutRepeatingOrSpammingOnRapidReentry() {
        var threat = new PostThreat();
        var owner = Optional.of(Faction.KOLONIEN);
        assertEquals(owner, threat.update(owner, 100, 300));
        assertTrue(threat.update(owner, 120, 300).isEmpty());
        threat.update(Optional.empty(), 140, 300);
        assertTrue(threat.update(owner, 160, 300).isEmpty());
        threat.update(Optional.empty(), 500, 300);
        assertEquals(owner, threat.update(owner, 520, 300));
    }

    @Test
    public void deliversAnAttackThatContinuesPastTheCooldownButDropsAnEndedAttack() {
        var threat = new PostThreat();
        var owner = Optional.of(Faction.KOLONIEN);
        assertEquals(owner, threat.update(owner, 100, 300));
        threat.update(Optional.empty(), 120, 300);
        assertTrue(threat.update(owner, 140, 300).isEmpty());
        assertTrue(threat.update(owner, 399, 300).isEmpty());
        assertEquals(owner, threat.update(owner, 400, 300));
        assertTrue(threat.update(owner, 401, 300).isEmpty());
        threat.update(Optional.empty(), 410, 300);
        threat.update(owner, 420, 300);
        threat.update(Optional.empty(), 430, 300);
        assertTrue(threat.update(Optional.empty(), 700, 300).isEmpty());
    }
}
