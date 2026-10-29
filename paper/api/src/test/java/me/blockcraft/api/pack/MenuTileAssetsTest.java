package me.blockcraft.api.pack;

import com.google.gson.JsonParser;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MenuTileAssetsTest {
    private static final Path PACK = Path.of("../../resourcepack/src/assets/blockcraft");

    @Test
    public void everyStateShipsItsBadgeAndTooltipSprites() throws IOException {
        for (var state : MenuState.values()) {
            String key = state.model().value();
            assertTrue(key, Files.exists(PACK.resolve("items/" + key + ".json")));
            assertTrue(key, Files.exists(PACK.resolve("models/item/" + key + ".json")));
            var badge = ImageIO.read(PACK.resolve("textures/item/" + key + ".png").toFile());
            assertEquals(16, badge.getWidth());
            assertEquals(16, badge.getHeight());
            for (String part : new String[] {"frame", "background"}) {
                assertTrue(key, Files.exists(PACK.resolve("textures/gui/sprites/tooltip/" + key + "_" + part + ".png")));
                assertTrue(key, Files.exists(PACK.resolve("textures/gui/sprites/tooltip/" + key + "_" + part + ".png.mcmeta")));
            }
        }
    }

    @Test
    public void everyPlacementHasTheRightSizedArtworkAndVerticalAlignment() throws IOException {
        var providers = new HashMap<Character, com.google.gson.JsonObject>();
        var font = JsonParser.parseString(Files.readString(PACK.resolve("font/tiles.json"))).getAsJsonObject();
        font.getAsJsonArray("providers").forEach(provider -> {
            var bitmap = provider.getAsJsonObject();
            providers.put(bitmap.getAsJsonArray("chars").get(0).getAsString().charAt(0), bitmap);
        });
        for (var tile : MenuTile.values()) {
            for (var shape : MenuTile.Shape.values()) {
                for (int row = 0; row + shape.rows() <= 6; row++) {
                    var placement = new MenuTile.Placement(row * 9, tile, shape);
                    var bitmap = providers.get(placement.glyph());
                    assertNotNull(tile + " " + shape + " row " + row, bitmap);
                    String expected = "blockcraft:font/tiles/" + tile.key() + "_" + shape.name().toLowerCase(Locale.ROOT) + ".png";
                    assertEquals(expected, bitmap.get("file").getAsString());
                    assertEquals(13 - 18 * (row + 1), bitmap.get("ascent").getAsInt());
                    var image = ImageIO.read(PACK.resolve("textures/" + expected.substring("blockcraft:".length())).toFile());
                    assertEquals(shape.width(), image.getWidth());
                    assertEquals(shape.height(), image.getHeight());
                    assertEquals(shape.height(), bitmap.get("height").getAsInt());
                    if (shape == MenuTile.Shape.LARGE && row == 0) {
                        var icon = ImageIO.read(PACK.resolve("textures/item/" + tile.icon().key() + ".png").toFile());
                        for (int y = 0; y < 16; y++) {
                            for (int x = 0; x < 16; x++) {
                                if ((icon.getRGB(x, y) >>> 24) != 0) {
                                    assertEquals(tile.key(), icon.getRGB(x, y), image.getRGB(18 + x, 4 + y));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void allGermanLabelsFitTheFontAndGlyphAdvancesAreFourPixels() throws IOException {
        Map<String, Object> messages;
        try (var input = Files.newInputStream(Path.of("src/main/resources/messages.yml"))) {
            messages = new Yaml().load(input);
        }
        var labels = (Map<?, ?>) messages.get("tiles");
        var font = JsonParser.parseString(Files.readString(PACK.resolve("font/label_large_1.json"))).getAsJsonObject();
        String chars = font.getAsJsonArray("providers").get(1).getAsJsonObject().getAsJsonArray("chars").get(0).getAsString();
        for (var tile : MenuTile.values()) {
            assertNotNull(tile.key(), labels.get(tile.key()));
            String label = labels.get(tile.key()).toString().toUpperCase(Locale.ROOT);
            assertTrue(label, label.length() <= 12);
            label.chars().filter(c -> c != ' ').forEach(c -> assertTrue(label, chars.indexOf(c) >= 0));
        }
        var sheet = ImageIO.read(PACK.resolve("textures/font/labels.png").toFile());
        for (int index = 0; index < chars.length(); index++) {
            boolean fullWidth = false;
            for (int y = 0; y < sheet.getHeight(); y++) {
                fullWidth |= (sheet.getRGB(index * 3 + 2, y) >>> 24) != 0;
            }
            assertTrue("Four-pixel advance for " + chars.charAt(index), fullWidth);
        }
    }
}
