package me.blockcraft.api.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The pack answers every icon and model the code asks for on a vanilla item, keeps the vanilla look as the fallback,
 * and ships every model and texture those answers draw. {@link PackOverrides#main} rewrites the overrides.
 */
public class PackOverridesTest {

    private static final Path PACK = Path.of("../../resourcepack/src");
    private static final Path OVERRIDES = PACK.resolve(PackOverrides.ITEMS);

    @Test
    public void everyOverrideSelectsWhatTheCodeAsksFor() throws IOException {
        for (var item : PackOverrides.cases().entrySet()) {
            var file = OVERRIDES.resolve(item.getKey() + ".json");
            assertTrue(item.getKey() + " has no override; run ./gradlew :paper:api:packOverrides", Files.exists(file));
            var model = read(file).getAsJsonObject("model");
            assertEquals(file.toString(), "minecraft:select", model.get("type").getAsString());
            assertEquals(file.toString(), "minecraft:custom_model_data", model.get("property").getAsString());
            assertTrue(file + " has no vanilla fallback", model.has("fallback"));
            var cases = new HashMap<String, String>();
            for (var entry : model.getAsJsonArray("cases")) {
                var match = entry.getAsJsonObject();
                cases.put(match.get("when").getAsString(), match.getAsJsonObject("model").get("model").getAsString());
            }
            assertEquals(file.toString(), item.getValue(), cases);
        }
    }

    @Test
    public void noOverrideOutlivesItsItem() throws IOException {
        try (var files = Files.list(OVERRIDES)) {
            var shipped = files.map(file -> file.getFileName().toString().replace(".json", "")).collect(Collectors.toSet());
            assertEquals(PackOverrides.cases().keySet(), shipped);
        }
    }

    @Test
    public void everyModelShipsWithItsTextures() throws IOException {
        var models = new HashSet<String>();
        PackOverrides.cases().values().forEach(cases -> models.addAll(cases.values()));
        for (var model : models) {
            assertTrue(model, Files.exists(asset(model, "models", ".json")));
        }
        try (var files = Files.walk(PACK.resolve("assets/blockcraft/models"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var textures = read(file).getAsJsonObject("textures");
                if (textures == null) {
                    continue;
                }
                for (var texture : textures.entrySet()) {
                    var name = texture.getValue().getAsString();
                    if (name.startsWith("blockcraft:")) {
                        assertTrue(file + " needs " + name, Files.exists(asset(name, "textures", ".png")));
                    }
                }
            }
        }
    }

    /** The client cannot bake a model drawing from both atlases, and then draws the whole item, every case of it, as missing. */
    @Test
    public void everyModelDrawsFromOneAtlas() throws IOException {
        try (var files = Files.walk(PACK.resolve("assets/blockcraft/models"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var textures = read(file).getAsJsonObject("textures");
                if (textures == null) {
                    continue;
                }
                var atlases = textures.entrySet().stream()
                        .map(texture -> texture.getValue().getAsString().split(":", 2)[1].split("/", 2)[0])
                        .collect(Collectors.toSet());
                assertEquals(file + " mixes atlases " + atlases, 1, atlases.size());
            }
        }
    }

    private static Path asset(String id, String kind, String extension) {
        var parts = id.split(":", 2);
        return PACK.resolve("assets").resolve(parts[0]).resolve(kind).resolve(parts[1] + extension);
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
}
