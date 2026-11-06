package me.blockcraft.sternenfront.menu;

import com.google.gson.JsonParser;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class TacticalMarkerAssetsTest {

    @Test
    public void mapBadgesUseTheHudLettersInTheItemAtlasWithFactionTint() throws Exception {
        var root = Path.of("../../resourcepack/src/assets/blockcraft");
        assertArrayEquals(Files.readAllBytes(root.resolve("textures/font/objective_pips.png")),
                Files.readAllBytes(root.resolve("textures/item/sternenfront/objective_pips.png")));
        for (int post = 0; post < 5; post++) {
            for (int variant = 0; variant < 2; variant++) {
                String name = "post_" + (char) ('a' + post) + (variant == 0 ? "_filled" : "_hollow");
                var route = JsonParser.parseString(Files.readString(root.resolve("items/sternenfront/" + name + ".json")))
                        .getAsJsonObject().getAsJsonObject("model");
                assertEquals("blockcraft:item/sternenfront/" + name, route.get("model").getAsString());
                assertEquals("minecraft:custom_model_data", route.getAsJsonArray("tints").get(0).getAsJsonObject().get("type").getAsString());
                var model = JsonParser.parseString(Files.readString(root.resolve("models/item/sternenfront/" + name + ".json"))).getAsJsonObject();
                assertEquals("blockcraft:item/sternenfront/objective_pips", model.getAsJsonObject("textures").get("marker").getAsString());
                var face = model.getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces").getAsJsonObject("south");
                assertEquals(0, face.get("tintindex").getAsInt());
                var uv = face.getAsJsonArray("uv");
                assertEquals((post * 2 + variant) * 1.6, uv.get(0).getAsDouble(), 0.001);
                assertEquals((post * 2 + variant + 1) * 1.6, uv.get(2).getAsDouble(), 0.001);
            }
        }
    }
}
