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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
            var cases = new HashMap<String, PackOverrides.Case>();
            for (var entry : model.getAsJsonArray("cases")) {
                var match = entry.getAsJsonObject();
                var drawn = match.getAsJsonObject("model");
                cases.put(match.get("when").getAsString(), new PackOverrides.Case(drawn.get("model").getAsString(), tintedByItem(drawn)));
            }
            assertEquals(file.toString(), item.getValue(), cases);
            if (cases.values().stream().anyMatch(drawn -> drawn.model().endsWith("_firing"))) {
                assertTrue(file + " must not lower the weapon for each firing pose", !read(file).get("hand_animation_on_swap").getAsBoolean());
            }
        }
    }

    /** A tinted model's colour comes from the item's first custom_model_data colour, not from a dye or a map. */
    private static boolean tintedByItem(JsonObject model) {
        if (!model.has("tints")) {
            return false;
        }
        var tints = model.getAsJsonArray("tints");
        if (tints.isEmpty()) {
            return false;
        }
        var tint = tints.get(0).getAsJsonObject();
        return tint.get("type").getAsString().equals("minecraft:custom_model_data") && tint.get("index").getAsInt() == 0;
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
        PackOverrides.cases().values().forEach(cases -> cases.values().forEach(drawn -> models.add(drawn.model())));
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

    @Test
    public void sharedBlazeRodModelsKeepTextureCoordinatesInsideTheAtlas() throws IOException {
        for (var drawn : PackOverrides.cases().get("blaze_rod").values()) {
            var model = read(asset(drawn.model(), "models", ".json"));
            for (var element : model.getAsJsonArray("elements")) {
                var box = element.getAsJsonObject();
                for (var entry : box.getAsJsonObject("faces").entrySet()) {
                    var face = entry.getValue().getAsJsonObject();
                    if (face.has("uv")) {
                        for (var coordinate : face.getAsJsonArray("uv")) {
                            assertTrue(drawn.model(), coordinate.getAsDouble() >= 0 && coordinate.getAsDouble() <= 16);
                        }
                    } else {
                        for (var key : new String[] {"from", "to"}) {
                            for (var coordinate : box.getAsJsonArray(key)) {
                                assertTrue(drawn.model() + " needs explicit UVs outside 0..16",
                                        coordinate.getAsDouble() >= 0 && coordinate.getAsDouble() <= 16);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void yardPropsKeepGroundOriginBoundsAndResolvedFaceUvs() throws IOException {
        int count = 0;
        for (var prop : PackModel.values()) {
            if (!prop.path().startsWith("sternenfront/yard/")) continue;
            count++;
            var model = read(asset(prop.model().asString(), "models", ".json"));
            assertFalse(prop.path(), model.has("parent"));
            assertFalse(prop.path(), model.has("display"));
            var textures = model.getAsJsonObject("textures");
            double bottom = Double.POSITIVE_INFINITY;
            for (var element : model.getAsJsonArray("elements")) {
                var box = element.getAsJsonObject();
                var from = box.getAsJsonArray("from");
                var to = box.getAsJsonArray("to");
                for (int axis = 0; axis < 3; axis++) assertTrue(prop.path(), from.get(axis).getAsDouble() < to.get(axis).getAsDouble());
                for (int corner = 0; corner < 8; corner++) {
                    var point = new double[3];
                    for (int axis = 0; axis < 3; axis++) point[axis] = ((corner & (1 << axis)) == 0 ? from : to).get(axis).getAsDouble();
                    assertYardBounds(prop.path(), point);
                    if (box.has("rotation")) {
                        var rotation = box.getAsJsonObject("rotation");
                        assertFalse(prop.path(), rotation.get("rescale").getAsBoolean());
                        int axis = "xyz".indexOf(rotation.get("axis").getAsString());
                        assertTrue(prop.path(), axis >= 0);
                        int a = (axis + 1) % 3, b = (axis + 2) % 3;
                        var origin = rotation.getAsJsonArray("origin");
                        double radians = Math.toRadians(rotation.get("angle").getAsDouble());
                        double x = point[a] - origin.get(a).getAsDouble(), y = point[b] - origin.get(b).getAsDouble();
                        point[a] = origin.get(a).getAsDouble() + x * Math.cos(radians) - y * Math.sin(radians);
                        point[b] = origin.get(b).getAsDouble() + x * Math.sin(radians) + y * Math.cos(radians);
                    }
                    assertYardBounds(prop.path(), point);
                    bottom = Math.min(bottom, point[1]);
                }
                assertFalse(prop.path(), box.has("light_emission"));
                for (var face : box.getAsJsonObject("faces").entrySet()) {
                    var drawn = face.getValue().getAsJsonObject();
                    var reference = drawn.get("texture").getAsString();
                    assertTrue(prop.path(), reference.startsWith("#"));
                    assertNotNull(prop.path(), textures.get(reference.substring(1)));
                    var uv = drawn.getAsJsonArray("uv");
                    assertEquals(prop.path(), 4, uv.size());
                    for (var coordinate : uv) assertTrue(prop.path(), coordinate.getAsDouble() >= 0 && coordinate.getAsDouble() <= 16);
                    assertTrue(prop.path(), uv.get(0).getAsDouble() < uv.get(2).getAsDouble());
                    assertTrue(prop.path(), uv.get(1).getAsDouble() < uv.get(3).getAsDouble());
                }
            }
            assertEquals(prop.path(), 8, bottom, .0001);
        }
        assertEquals(6, count);
    }

    private static void assertYardBounds(String path, double[] point) {
        assertTrue(path, point[0] >= -8 && point[0] <= 24);
        assertTrue(path, point[1] >= 8 && point[1] <= 32);
        assertTrue(path, point[2] >= -8 && point[2] <= 24);
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
}
