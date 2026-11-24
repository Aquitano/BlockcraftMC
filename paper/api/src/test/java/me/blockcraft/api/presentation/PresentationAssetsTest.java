package me.blockcraft.api.presentation;

import com.google.gson.JsonParser;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class PresentationAssetsTest {
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
