package me.blockcraft.endergames.kit;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class KitTest {

    @Test
    public void everyKitIsFoundByItsUnlock() {
        for (var kit : Kit.values()) {
            assertEquals(Optional.of(kit), Kit.byItem(kit.item()));
        }
    }

    @Test
    public void otherUnlocksAreNoKits() {
        assertEquals(Optional.empty(), Kit.byItem("qsg.kit.diamond"));
        assertEquals(Optional.empty(), Kit.byItem(Kit.ITEM_PREFIX + "unknown"));
    }
}
