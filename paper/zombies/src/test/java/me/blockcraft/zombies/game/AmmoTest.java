package me.blockcraft.zombies.game;

import org.junit.Test;

import static org.junit.Assert.*;

public class AmmoTest {

    @Test
    public void reloadTransfersOnlyTheAvailableReserveAndCannotFireEarly() {
        var ammo = new Ammo(3, 2);
        assertTrue(ammo.shoot(0, 2));
        assertFalse(ammo.shoot(1, 2));
        assertTrue(ammo.shoot(2, 2));
        assertTrue(ammo.shoot(4, 2));
        assertTrue(ammo.reload(5, 10));
        assertFalse(ammo.shoot(12, 2));
        assertFalse(ammo.tick(14));
        assertTrue(ammo.tick(15));
        assertEquals(2, ammo.clip());
        assertEquals(0, ammo.reserve());
        assertFalse(ammo.reload(16, 10));
    }

    @Test
    public void refillCancelsPendingReloadAndUpgradeKeepsItsState() {
        var ammo = new Ammo(12, 120);
        ammo.shoot(0, 1);
        ammo.reload(1, 20);
        ammo.upgrade();
        assertTrue(ammo.upgraded());
        assertFalse(ammo.reloading());
        assertFalse(ammo.tick(21));
        assertEquals(12, ammo.clip());
        assertEquals(120, ammo.reserve());
    }
}
