package me.blockcraft.api.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The code's idea of the pack's font must match the font the pack ships. */
public class UiFontTest {

    private static final Path FONT = Path.of("../../resourcepack/src/assets/blockcraft/font/ui.json");
    private static final Path RANKS = Path.of("../../luckperms/ranks.json");
    private static final String BADGE = "meta.badge.";

    @Test
    public void configuredLabelsKeepTheCursorInPlaceForPunctuationWhitespaceAndSupplementaryCharacters() throws IOException {
        var fonts = new HashMap<Key, Map<Integer, Integer>>();
        for (String name : new String[] {"ui", "tiles", "label_wide_1", "label_large_1"}) {
            fonts.put(Key.key("blockcraft", name), fontAdvances(name));
        }
        var labels = Map.of("PvP / + " + Character.toString(0x1F409), "PVP / + ?",
                "Köpfe & Co", "KÖPFE & CO", "A\nB\tC\u00a0D", "A B C D");
        for (var shape : MenuTile.Shape.values()) {
            var tile = new MenuTile.Placement(shape == MenuTile.Shape.WIDE ? 14 : 12, MenuTile.DUELL, shape);
            for (var label : labels.entrySet()) {
                var rendered = UiFont.tile(tile, Component.text(label.getKey()));
                assertEquals(label.getValue(), PlainTextComponentSerializer.plainText().serialize(rendered).replaceAll("\\p{Co}", ""));
                assertEquals(label.getKey(), 0, width(rendered, null, fonts));
            }
        }
    }

    private static int width(Component component, Key inherited, Map<Key, Map<Integer, Integer>> fonts) {
        Key font = component.font() == null ? inherited : component.font();
        int width = 0;
        if (component instanceof TextComponent text) {
            for (int codepoint : text.content().codePoints().toArray()) {
                Integer advance = fonts.getOrDefault(font, Map.of()).get(codepoint);
                assertNotNull(font + " glyph " + Integer.toHexString(codepoint), advance);
                width += advance;
            }
        }
        for (var child : component.children()) {
            width += width(child, font, fonts);
        }
        return width;
    }

    private static Map<Integer, Integer> fontAdvances(String name) throws IOException {
        var advances = new HashMap<Integer, Integer>();
        var font = JsonParser.parseString(Files.readString(FONT.resolveSibling(name + ".json"))).getAsJsonObject();
        for (var entry : font.getAsJsonArray("providers")) {
            var provider = entry.getAsJsonObject();
            if (provider.get("type").getAsString().equals("space")) {
                provider.getAsJsonObject("advances").entrySet().forEach(space ->
                        advances.put(space.getKey().codePointAt(0), space.getValue().getAsInt()));
                continue;
            }
            String file = provider.get("file").getAsString().substring("blockcraft:".length());
            var image = ImageIO.read(FONT.getParent().getParent().resolve("textures/" + file).toFile());
            var rows = provider.getAsJsonArray("chars");
            int cellHeight = image.getHeight() / rows.size();
            double scale = provider.get("height").getAsDouble() / cellHeight;
            for (int row = 0; row < rows.size(); row++) {
                var codepoints = rows.get(row).getAsString().codePoints().toArray();
                int cellWidth = image.getWidth() / codepoints.length;
                for (int column = 0; column < codepoints.length; column++) {
                    int inkWidth = 0;
                    for (int x = 0; x < cellWidth; x++) {
                        for (int y = 0; y < cellHeight; y++) {
                            if ((image.getRGB(column * cellWidth + x, row * cellHeight + y) >>> 24) != 0) {
                                inkWidth = x + 1;
                            }
                        }
                    }
                    advances.put(codepoints[column], (int) Math.round(inkWidth * scale) + 1);
                }
            }
        }
        return advances;
    }

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
            assertTrue("card panel for " + rows + " rows", bitmaps.contains((char) (0xE120 + rows)));
        }
        for (var icon : Icon.values()) {
            assertTrue(icon.name(), bitmaps.contains(icon.glyph()));
        }
        for (int pixels : new int[] {-8, -169, 1, 255, -255}) {
            assertEquals(pixels, width(UiFont.shift(pixels), advances));
        }
    }

    @Test
    public void everyRankBadgeIsInThePacksFont() throws IOException {
        var bitmaps = new HashSet<Character>();
        for (var provider : JsonParser.parseString(Files.readString(FONT)).getAsJsonObject().getAsJsonArray("providers")) {
            var json = provider.getAsJsonObject();
            if (json.get("type").getAsString().equals("bitmap")) {
                json.getAsJsonArray("chars").forEach(row -> row.getAsString().chars().forEach(c -> bitmaps.add((char) c)));
            }
        }
        var badges = new HashMap<String, String>();
        JsonParser.parseString(Files.readString(RANKS)).getAsJsonObject().getAsJsonObject("groups").entrySet()
                .forEach(group -> group.getValue().getAsJsonObject().getAsJsonArray("nodes").forEach(node -> {
                    String key = node.getAsJsonObject().get("key").getAsString();
                    if (key.startsWith(BADGE)) {
                        badges.put(group.getKey(), key.substring(BADGE.length()));
                    }
                }));
        assertFalse("no badges in " + RANKS, badges.isEmpty());
        badges.forEach((group, glyph) ->
                assertTrue(group + "'s badge", glyph.length() == 1 && bitmaps.contains(glyph.charAt(0))));
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
