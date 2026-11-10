package me.blockcraft.api.presentation;

import com.google.gson.JsonParser;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PresentationAssetsTest {
    @Test
    public void everyEffectModelTextureExistsInTheVanillaItemAtlasDirectory() throws Exception {
        var assets = Path.of("../../resourcepack/src/assets/blockcraft");
        for (String name : List.of("ring", "crystal", "flare")) {
            var item = JsonParser.parseString(Files.readString(assets.resolve("items/presentation/" + name + ".json"))).getAsJsonObject();
            var reference = item.getAsJsonObject("model").get("model").getAsString().substring("blockcraft:".length());
            var model = JsonParser.parseString(Files.readString(assets.resolve("models/" + reference + ".json"))).getAsJsonObject();
            for (var texture : model.getAsJsonObject("textures").asMap().values()) {
                String path = texture.getAsString().substring("blockcraft:".length());
                assertTrue("Texture is outside Minecraft's item atlas: " + path, path.startsWith("item/"));
                assertTrue("Missing texture: " + path, Files.isRegularFile(assets.resolve("textures/" + path + ".png")));
            }
        }
    }

    @Test
    public void gameplayEffectLevelsHaveMatchingShaderIntensities() throws Exception {
        var root = Path.of("../../resourcepack/src/assets/blockcraft/post_effect");
        for (var atmosphere : ScreenEffects.Atmosphere.values()) {
            for (int level = 1; level <= 8; level++) {
                var key = ScreenEffects.accentKey(atmosphere, level / 8.0);
                var chain = JsonParser.parseString(Files.readString(root.resolve(key.value() + ".json"))).getAsJsonObject();
                var parameters = chain.getAsJsonArray("passes").get(0).getAsJsonObject().getAsJsonObject("uniforms")
                        .getAsJsonArray("PresentationConfig").get(0).getAsJsonObject().getAsJsonArray("value");
                assertEquals(level / 8.0, parameters.get(3).getAsDouble(), 0.00001);
            }
        }
    }
}
