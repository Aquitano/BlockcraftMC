package me.blockcraft.lobby.cosmetic;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HeadTest {

    @Test
    public void descriptiveKeysKeepExistingPurchasesAndSelections() {
        assertEquals(Head.HAMBURGER, Head.byKey("hamburger").orElseThrow());
        assertEquals(Head.HAMBURGER, Head.byKey("a").orElseThrow());
        assertEquals("lobby.head.a", Head.HAMBURGER.item());
        assertEquals(Head.COMMAND_BLOCK, Head.byKey("abss").orElseThrow());
        assertEquals("lobby.head.abss", Head.COMMAND_BLOCK.item());
    }
}
