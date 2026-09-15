package me.blockcraft.api.pack;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** Every icon the code offers must have its model and texture in the pack. */
public class MenuIconTest {

    private static final Path PACK = Path.of("../../resourcepack/src/assets/blockcraft");

    @Test
    public void everyIconShipsInThePack() {
        for (var icon : MenuIcon.values()) {
            assertTrue(icon.key() + " model", Files.exists(PACK.resolve("items/" + icon.key() + ".json")));
            assertTrue(icon.key() + " texture", Files.exists(PACK.resolve("textures/item/" + icon.key() + ".png")));
        }
        assertTrue("empty model", Files.exists(PACK.resolve("items/" + MenuIcon.EMPTY_MODEL.value() + ".json")));
    }
}
