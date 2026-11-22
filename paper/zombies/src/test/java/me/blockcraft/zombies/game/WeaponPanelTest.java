package me.blockcraft.zombies.game;

import com.google.gson.JsonParser;
import me.blockcraft.api.pack.HudPanel;
import me.blockcraft.api.pack.HudPanel.Tint;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The client only shows a mismatch between the panel's layout and the pack's font as a garbled corner. */
public class WeaponPanelTest {

    private static final Path ASSETS = Path.of("../../resourcepack/src/assets/blockcraft");

    private record Cell(int advance, int height) { }

    /** The codepoints each of the font's textures draws, from the first, and every glyph's cell. */
    private record Font(Map<String, List<Integer>> files, Map<Integer, Cell> cells) { }

    @Test
    public void everyGlyphStaysInsideThePanelAndAdvancesAsTheFontDraws() throws IOException {
        var font = font();
        for (var weapon : Weapon.values()) {
            assertEquals(Integer.valueOf(WeaponPanel.FIRST_GUN + weapon.ordinal()), font.files().get("gun_" + weapon.key() + ".png").getFirst());
        }
        var reloading = new Ammo(60, 360);
        reloading.shoot(0, 1);
        reloading.reload(1, 40);
        for (var ammo : List.of(new Ammo(60, 360), new Ammo(5, 0), reloading)) {
            for (var weapon : Weapon.values()) {
                for (var glyph : WeaponPanel.layout(weapon, ammo, 21)) {
                    var cell = font.cells().get(glyph.text().codePointAt(0));
                    assertEquals(glyph.toString(), cell.advance(), glyph.advance());
                    assertTrue(glyph.toString(), glyph.x() >= -WeaponPanel.WIDTH && glyph.x() + glyph.advance() - 1 <= 0);
                    assertTrue(glyph.toString(), glyph.y() >= HudPanel.BOTTOM_RIGHT_HEIGHT - WeaponPanel.HEIGHT
                            && glyph.y() + cell.height() <= HudPanel.BOTTOM_RIGHT_HEIGHT);
                }
            }
        }
    }

    @Test
    public void theBarShowsTheClipUntilAReloadRunsAndThenTheReload() {
        var ammo = new Ammo(60, 360);
        assertEquals(new Bar((char) (WeaponPanel.TRACK + 28), Tint.WHITE), bar(ammo, 0));
        for (int shot = 0; shot < 50; shot++) {
            ammo.shoot(shot, 1);
        }
        assertEquals(new Bar((char) (WeaponPanel.TRACK + 5), Tint.RED), bar(ammo, 50));
        ammo.reload(100, 40);
        assertEquals(new Bar((char) (WeaponPanel.TRACK + 7), Tint.GOLD), bar(ammo, 110));
    }

    private record Bar(char fill, Tint tint) { }

    private static Bar bar(Ammo ammo, int tick) {
        var fill = WeaponPanel.layout(Weapon.RIFLE, ammo, tick).getLast();
        return new Bar(fill.text().charAt(0), fill.tint());
    }

    /**
     * {@code blockcraft:zombies/weapon} with each glyph's advance as the client measures it: up to its last inked column,
     * scaled to its height, plus one.
     */
    private static Font font() throws IOException {
        var files = new HashMap<String, List<Integer>>();
        var cells = new HashMap<Integer, Cell>();
        var providers = JsonParser.parseString(Files.readString(ASSETS.resolve("font/zombies/weapon.json"))).getAsJsonObject()
                .getAsJsonArray("providers");
        for (var entry : providers) {
            var provider = entry.getAsJsonObject();
            var file = provider.get("file").getAsString().substring("blockcraft:".length());
            var image = ImageIO.read(ASSETS.resolve("textures/" + file).toFile());
            var codepoints = provider.getAsJsonArray("chars").get(0).getAsString().codePoints().boxed().toList();
            int height = provider.get("height").getAsInt();
            double scale = (double) height / image.getHeight();
            int cellWidth = image.getWidth() / codepoints.size();
            for (int column = 0; column < codepoints.size(); column++) {
                int ink = 0;
                for (int x = 0; x < cellWidth; x++) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        if ((image.getRGB(column * cellWidth + x, y) >>> 24) != 0) {
                            ink = x + 1;
                        }
                    }
                }
                cells.put(codepoints.get(column), new Cell((int) Math.round(ink * scale) + 1, height));
            }
            files.put(file.substring(file.lastIndexOf('/') + 1), codepoints);
        }
        return new Font(files, cells);
    }
}
