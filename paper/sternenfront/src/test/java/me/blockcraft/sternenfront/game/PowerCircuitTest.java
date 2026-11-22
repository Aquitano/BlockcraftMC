package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PowerCircuitTest {
    @Test
    public void sabotageAndConsoleOverrideHaveSeparateEffects() {
        var circuit = new PowerCircuit();
        circuit.console(false, 100, 40);
        assertTrue(circuit.powered());
        assertFalse(circuit.secure(139));
        assertTrue(circuit.secure(140));
        circuit.power(false);
        circuit.console(true, 150, 40);
        assertFalse(circuit.secure(150));
        circuit.power(true);
        assertTrue(circuit.secure(150));
        circuit.console(false, 160, 40);
        circuit.console(true, 170, 40);
        assertTrue(circuit.secure(170));
        circuit.reset();
        assertTrue(circuit.secure(0));
    }
}
