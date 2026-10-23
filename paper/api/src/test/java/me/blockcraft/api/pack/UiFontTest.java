package me.blockcraft.api.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The code's idea of the pack's font must match the font the pack ships. */
public class UiFontTest {

    private static final Path FONT = Path.of("../../resourcepack/src/assets/blockcraft/font/ui.json");

    @Test
    public void everyGlyphTheCodeUsesIsInThePacksFont() throws IOException {
        var bitmaps = new HashSet<Character>();
        var advances = new HashMap<Character, Integer>();
        for (var provider : JsonParser.parseString(Files.readString(FONT)).getAsJsonObject().getAsJsonArray("providers")) {
            var json = provider.getAsJsonObject();
            switch (json.get("type").getAsString()) {
                case "bitmap" -> json.getAsJsonArray("chars").forEach(row -> row.getAsString().chars().forEach(c -> bitmaps.add((char) c)));
                case "space" -> json.getAsJsonObject("advances").entrySet()
                        .forEach(entry -> advances.put(entry.getKey().charAt(0), entry.getValue().getAsInt()));
                default -> throw new IllegalStateException("Unexpected provider " + json);
            }
        }
        for (int rows = 1; rows <= 6; rows++) {
            assertTrue("panel for " + rows + " rows", bitmaps.contains(UiFont.panel(rows, false)));
        }
        for (int rows = 2; rows <= 6; rows++) {
            assertTrue("toolbar panel for " + rows + " rows", bitmaps.contains(UiFont.panel(rows, true)));
        }
        for (var icon : Icon.values()) {
            assertTrue(icon.name(), bitmaps.contains(icon.glyph()));
        }
        for (int pixels : new int[] {-8, -169, 1, 255, -255}) {
            assertEquals(pixels, width(UiFont.shift(pixels), advances));
        }
    }

    private static int width(String glyphs, Map<Character, Integer> advances) {
        int width = 0;
        for (char glyph : glyphs.toCharArray()) {
            assertTrue("space glyph " + Integer.toHexString(glyph), advances.containsKey(glyph));
            width += advances.get(glyph);
        }
        return width;
    }
}
