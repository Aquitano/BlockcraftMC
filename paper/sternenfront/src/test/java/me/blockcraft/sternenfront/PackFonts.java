package me.blockcraft.sternenfront;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;

/** The pack's fonts as the client measures them, for text that is laid out glyph by glyph. */
public final class PackFonts {

    public static final Path FONTS = Path.of("../../resourcepack/src/assets/blockcraft/font");

    private PackFonts() {
    }

    public static JsonArray providers(String name) throws IOException {
        return JsonParser.parseString(Files.readString(FONTS.resolve(name + ".json"))).getAsJsonObject().getAsJsonArray("providers");
    }

    /** The first character the font {@code name} draws from the texture {@code file}. */
    public static char first(String name, String file) throws IOException {
        for (var provider : providers(name)) {
            var json = provider.getAsJsonObject();
            if (json.has("file") && json.get("file").getAsString().equals("blockcraft:font/" + file)) {
                return json.getAsJsonArray("chars").get(0).getAsString().charAt(0);
            }
        }
        throw new AssertionError(name + " draws nothing from " + file);
    }

    /** Each glyph's advance as the client measures it: up to its last inked column, scaled to its height, plus one. */
    public static Map<Integer, Integer> advances(String name) throws IOException {
        var advances = new HashMap<Integer, Integer>();
        for (var entry : providers(name)) {
            var provider = entry.getAsJsonObject();
            if (provider.get("type").getAsString().equals("space")) {
                provider.getAsJsonObject("advances").entrySet().forEach(space ->
                        advances.put(space.getKey().codePointAt(0), space.getValue().getAsInt()));
                continue;
            }
            String file = provider.get("file").getAsString().substring("blockcraft:".length());
            var image = ImageIO.read(FONTS.getParent().resolve("textures/" + file).toFile());
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

    /** How far {@code component} moves the cursor; every glyph must be in one of {@code fonts}. */
    public static int width(Component component, Map<Key, Map<Integer, Integer>> fonts) {
        return width(component, null, fonts);
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
}
