package me.blockcraft.api.dialogue;

import com.google.gson.JsonParser;
import me.blockcraft.api.pack.HudPanel;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DialogueAssetsTest {
    /** A glyph with the default font's ascent starts on its line's top, where {@code HudPanel} counts its row from. */
    private static final int HANGS_FROM_LINE_TOP = 7;

    @Test
    public void generatedGlyphWidthsMatchTheClientAndAllPortraitsExist() throws Exception {
        Path assets = Path.of("../../resourcepack/src/assets/blockcraft");
        var font = JsonParser.parseString(Files.readString(assets.resolve("font/dialogue_text.json"))).getAsJsonObject();
        var provider = font.getAsJsonArray("providers").get(1).getAsJsonObject();
        var texture = ImageIO.read(assets.resolve("textures/gui/dialogue/letters.png").toFile());
        var rows = provider.getAsJsonArray("chars");
        int cellHeight = texture.getHeight() / rows.size();
        assertEquals(HANGS_FROM_LINE_TOP, provider.get("ascent").getAsInt());
        for (int row = 0; row < rows.size(); row++) {
            int[] characters = rows.get(row).getAsString().codePoints().toArray();
            int cellWidth = texture.getWidth() / characters.length;
            for (int column = 0; column < characters.length; column++) {
                if (characters[column] == 0) continue;
                int ink = 0;
                for (int x = 0; x < cellWidth; x++) {
                    for (int y = 0; y < cellHeight; y++) {
                        if ((texture.getRGB(column * cellWidth + x, row * cellHeight + y) >>> 24) != 0) ink = x + 1;
                    }
                }
                assertEquals(Character.toString(characters[column]), ink + 1, DialogueText.width(Character.toString(characters[column])));
            }
        }
        var art = JsonParser.parseString(Files.readString(assets.resolve("font/dialogue.json"))).getAsJsonObject().getAsJsonArray("providers");
        var panels = List.of(DialogueView.SHORT_HEIGHT, DialogueView.SHORT_HEIGHT, DialogueView.TALL_HEIGHT, DialogueView.TALL_HEIGHT);
        assertEquals(panels.size() + Dialogue.Portrait.values().length, art.size());
        int panelWidth = 0;
        for (int index = 0; index < art.size(); index++) {
            var image = art.get(index).getAsJsonObject();
            assertEquals(HANGS_FROM_LINE_TOP, image.get("ascent").getAsInt());
            String path = image.get("file").getAsString().substring("blockcraft:".length());
            var file = assets.resolve("textures").resolve(path);
            assertTrue(Files.exists(file));
            var picture = ImageIO.read(file.toFile());
            assertTrue(path + " is wider than the client's 256-pixel font atlas", picture.getWidth() <= 256);
            assertEquals(path, picture.getHeight(), image.get("height").getAsInt());
            if (index < panels.size()) {
                assertEquals(path, (int) panels.get(index), picture.getHeight());
                panelWidth += index < 2 ? picture.getWidth() : 0;
            }
        }
        assertEquals(DialogueView.WIDTH, panelWidth);
    }

    /**
     * The client picks the largest GUI scale that leaves at least 240 pixels of height, where the crosshair's 15 pixels
     * end 127 pixels down; the tallest panel stands on the bottom-centre space and must end its top below them.
     */
    @Test
    public void theTallestPanelStaysBelowTheCrosshairOnTheShortestGui() {
        int gui = 240;
        int crosshairEnd = (gui - 15) / 2 + 15;
        int panelTop = gui - HudPanel.BOTTOM_CENTRE_BOTTOM - DialogueView.height(DialogueView.ROWS);
        assertTrue(panelTop + " is not below " + crosshairEnd, panelTop > crosshairEnd);
    }

    @Test
    public void bundledDemoReferencesOnlyExistingGermanMessagesAndValidBranches() throws Exception {
        var config = YamlFile.loadWithDefaults(Path.of("/nonexistent-dialogue-config"),
                () -> getClass().getResourceAsStream("/config.yml"));
        var demos = List.of(YamlFile.section(YamlFile.section(config, "dialogue"), "demo"),
                YamlFile.section(YamlFile.section(YamlFile.section(config, "presentation"), "discovery"), "demo"));
        var messages = Messages.load(Path.of("/nonexistent-dialogue-messages"),
                () -> getClass().getResourceAsStream("/messages.yml"));
        for (var demo : demos) {
            var dialogue = Dialogue.load(demo);
            assertTrue(dialogue.problems().toString(), dialogue.valid());
            for (var line : dialogue.dialogue().lines().values()) {
                assertTrue(messages.has(line.speaker()));
                assertTrue(messages.has(line.text()));
                for (var choice : line.choices()) assertTrue(messages.has(choice.text()));
            }
        }
    }
}
