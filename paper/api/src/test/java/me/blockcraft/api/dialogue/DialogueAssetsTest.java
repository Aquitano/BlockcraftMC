package me.blockcraft.api.dialogue;

import com.google.gson.JsonParser;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DialogueAssetsTest {
    @Test
    public void generatedGlyphWidthsMatchTheClientAndAllPortraitsExist() throws Exception {
        Path assets = Path.of("../../resourcepack/src/assets/blockcraft");
        var font = JsonParser.parseString(Files.readString(assets.resolve("font/dialogue_body_0.json"))).getAsJsonObject();
        var provider = font.getAsJsonArray("providers").get(1).getAsJsonObject();
        var texture = ImageIO.read(assets.resolve("textures/gui/dialogue/letters.png").toFile());
        var rows = provider.getAsJsonArray("chars");
        int cellHeight = texture.getHeight() / rows.size();
        assertTrue(provider.get("ascent").getAsInt() <= provider.get("height").getAsInt());
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
        var art = JsonParser.parseString(Files.readString(assets.resolve("font/dialogue.json"))).getAsJsonObject();
        assertEquals(7, art.getAsJsonArray("providers").size());
        for (int index = 0; index <= Dialogue.Portrait.values().length; index++) {
            var image = art.getAsJsonArray("providers").get(index).getAsJsonObject();
            assertTrue(image.get("ascent").getAsInt() <= image.get("height").getAsInt());
            String path = image.get("file").getAsString().substring("blockcraft:".length());
            assertTrue(Files.exists(assets.resolve("textures").resolve(path)));
        }
    }

    @Test
    public void bundledDemoReferencesOnlyExistingGermanMessagesAndValidBranches() throws Exception {
        var config = YamlFile.loadWithDefaults(Path.of("/nonexistent-dialogue-config"),
                () -> getClass().getResourceAsStream("/config.yml"));
        var dialogue = Dialogue.load(YamlFile.section(YamlFile.section(config, "dialogue"), "demo"));
        assertTrue(dialogue.problems().toString(), dialogue.valid());
        var messages = Messages.load(Path.of("/nonexistent-dialogue-messages"),
                () -> getClass().getResourceAsStream("/messages.yml"));
        for (var line : dialogue.dialogue().lines().values()) {
            assertTrue(messages.has(line.speaker()));
            assertTrue(messages.has(line.text()));
            for (var choice : line.choices()) assertTrue(messages.has(choice.text()));
        }
    }
}
