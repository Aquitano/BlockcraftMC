package me.blockcraft.api.presentation;

import com.google.gson.JsonParser;
import io.papermc.paper.entity.PlayerPostEffects;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SequencedCollection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ScreenEffectsTest {
    @Test
    public void replacingFramesAndClearingPresentationPreservesOtherPluginsEffects() {
        var values = new ArrayList<>(List.of(Key.key("other", "weather")));
        var effects = (PlayerPostEffects) Proxy.newProxyInstance(PlayerPostEffects.class.getClassLoader(),
                new Class<?>[]{PlayerPostEffects.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "values" -> List.copyOf(values);
                    case "set" -> {
                        values.clear();
                        for (var value : (SequencedCollection<?>) args[0]) values.add((Key) value);
                        yield true;
                    }
                    default -> throw new AssertionError(method.getName());
                });
        var player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("postEffects")) return effects;
                    throw new AssertionError(method.getName());
                });
        ScreenEffects.atmosphere(player, ScreenEffects.Atmosphere.STORM);
        ScreenEffects.frame(player, 1, true);
        ScreenEffects.frame(player, 0.5, true);
        assertEquals(List.of(Key.key("other", "weather"), Key.key("blockcraft", "presentation/atmosphere/storm"),
                Key.key("blockcraft", "presentation/cinematic/letterbox/16")), values);
        ScreenEffects.accent(player, ScreenEffects.Atmosphere.FOCUS, 0.5);
        ScreenEffects.clearAccent(player);
        assertEquals(List.of(Key.key("other", "weather"), Key.key("blockcraft", "presentation/atmosphere/storm"),
                Key.key("blockcraft", "presentation/cinematic/letterbox/16")), values);
        ScreenEffects.clearAtmosphere(player);
        assertEquals(List.of(Key.key("other", "weather"), Key.key("blockcraft", "presentation/cinematic/letterbox/16")), values);
        ScreenEffects.clear(player);
        assertEquals(List.of(Key.key("other", "weather")), values);
    }

    @Test
    public void everyFadeStepReferencesAPostChainWithMatchingParameters() throws Exception {
        var root = Path.of("../../resourcepack/src/assets/blockcraft/post_effect");
        assertNull(ScreenEffects.frameKey(-1, false));
        assertNull(ScreenEffects.frameKey(Double.NaN, false));
        for (boolean letterbox : List.of(false, true)) {
            for (int step = 0; step <= 32; step++) {
                var key = ScreenEffects.frameKey(step / 32.0, letterbox);
                if (key == null) continue;
                var chain = JsonParser.parseString(Files.readString(root.resolve(key.value() + ".json"))).getAsJsonObject();
                var parameters = chain.getAsJsonArray("passes").get(0).getAsJsonObject().getAsJsonObject("uniforms")
                        .getAsJsonArray("PresentationConfig").get(0).getAsJsonObject().getAsJsonArray("value");
                assertEquals(step / 32.0, parameters.get(0).getAsDouble(), 0.00001);
                assertEquals(letterbox ? 1 : 0, parameters.get(1).getAsInt());
            }
        }
        for (var atmosphere : ScreenEffects.Atmosphere.values()) {
            assertTrue(Files.exists(root.resolve("presentation/atmosphere/" + atmosphere.key() + ".json")));
        }
    }
}
