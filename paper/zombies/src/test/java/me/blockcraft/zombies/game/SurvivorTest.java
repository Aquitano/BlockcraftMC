package me.blockcraft.zombies.game;

import org.junit.Test;

import static org.junit.Assert.*;

public class SurvivorTest {

    @Test
    public void interruptedReviveResetsAndCompletionOnlyHappensOnce() {
        var survivor = new Survivor(100);
        assertTrue(survivor.down(10, 100));
        assertFalse(survivor.down(11, 100));
        assertFalse(survivor.revive(10, 20));
        assertFalse(survivor.revive(0, 20));
        assertEquals(0, survivor.reviveProgress());
        assertFalse(survivor.revive(10, 20));
        assertTrue(survivor.revive(10, 20));
        assertTrue(survivor.fighting());
        assertFalse(survivor.revive(20, 20));
        assertFalse(survivor.bleed(200));
    }

    @Test
    public void bleedingLosesPerksButKeepsWeaponsAndGoldForTheNextWave() {
        var survivor = new Survivor(100);
        survivor.perks().add(Perk.EXTRA_HEALTH);
        survivor.weapons().put(Weapon.PISTOL, new Ammo(12, 120));
        survivor.down(10, 100);
        assertFalse(survivor.bleed(109));
        assertTrue(survivor.bleed(110));
        assertFalse(survivor.bleed(111));
        assertTrue(survivor.perks().isEmpty());
        assertEquals(1, survivor.weapons().size());
        assertEquals(100, survivor.gold());
        survivor.recover();
        assertTrue(survivor.fighting());
    }

    @Test
    public void purchasesCannotOverdrawOrCreateGold() {
        var survivor = new Survivor(100);
        assertFalse(survivor.spend(-5));
        assertFalse(survivor.spend(101));
        assertTrue(survivor.spend(100));
        assertEquals(0, survivor.gold());
    }
}
