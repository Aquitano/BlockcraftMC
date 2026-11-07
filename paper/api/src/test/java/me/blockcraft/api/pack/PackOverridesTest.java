package me.blockcraft.api.pack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
                boolean guarded = drawn.get("type").getAsString().equals("minecraft:condition");
                var shown = guarded ? drawn.getAsJsonObject("on_false") : drawn;
                var found = new PackOverrides.Case(shown.get("model").getAsString(), tintedByItem(shown), guarded);
                if (guarded) {
                    assertEquals(file.toString(), "minecraft:using_item", drawn.get("property").getAsString());
                    var guard = drawn.getAsJsonObject("on_true");
                    assertEquals(file.toString(), found.guard(), guard.get("model").getAsString());
                    assertEquals(file.toString(), found.tinted(), tintedByItem(guard));
                }
                cases.put(match.get("when").getAsString(), found);
            }
            assertEquals(file.toString(), item.getValue(), cases);
            if (PackOverrides.swapsInPlace(cases)) {
                assertTrue(file + " must not lower the weapon for each swap in place", !read(file).get("hand_animation_on_swap").getAsBoolean());
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
        PackOverrides.cases().values().forEach(cases -> cases.values().forEach(drawn -> {
            models.add(drawn.model());
            if (drawn.guarded()) {
                models.add(drawn.guard());
            }
        }));
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
        assertEquals(9, count);
    }

    @Test
    public void medicalCapsulesKeepClearWindowsInSingleFacesWithBinaryAlpha() throws IOException {
        for (var id : List.of("clone_tank", "embryo_rack", "gene_archive")) {
            var atlas = ImageIO.read(PACK.resolve("assets/blockcraft/textures/item/sternenfront/yard/" + id + ".png").toFile());
            assertEquals(id, 64, atlas.getWidth());
            assertEquals(id, 64, atlas.getHeight());
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                int alpha = atlas.getRGB(x, y) >>> 24;
                assertTrue(id, alpha == 0 || alpha == 255);
            }
            var model = read(PACK.resolve("assets/blockcraft/models/item/sternenfront/yard/" + id + ".json"));
            int windows = 0;
            for (var element : model.getAsJsonArray("elements")) {
                var faces = element.getAsJsonObject().getAsJsonObject("faces");
                for (var entry : faces.entrySet()) {
                    var uv = entry.getValue().getAsJsonObject().getAsJsonArray("uv");
                    int clear = 0, opaque = 0;
                    for (int y = (int) (uv.get(1).getAsDouble() * 4); y < uv.get(3).getAsDouble() * 4; y++) {
                        for (int x = (int) (uv.get(0).getAsDouble() * 4); x < uv.get(2).getAsDouble() * 4; x++) {
                            if (atlas.getRGB(x, y) >>> 24 == 0) clear++;
                            else opaque++;
                        }
                    }
                    if (clear == 0) continue;
                    if (entry.getKey().equals("up") || entry.getKey().equals("down")) {
                        assertTrue(id, opaque > clear);
                        continue;
                    }
                    windows++;
                    assertEquals(id, 1, faces.size());
                    assertTrue(id, clear > opaque);
                    assertTrue(id, opaque > 0);
                }
            }
            assertEquals(id, id.equals("clone_tank") ? 5 : id.equals("embryo_rack") ? 9 : 0, windows);
        }
    }

    private static void assertYardBounds(String path, double[] point) {
        double[] bounds = switch (path) {
            case "sternenfront/yard/clone_tank" -> new double[]{0, 8, 0, 16, 32, 16};
            case "sternenfront/yard/embryo_rack" -> new double[]{-4, 8, 1.6, 20, 22.4, 14.4};
            case "sternenfront/yard/gene_archive" -> new double[]{2.4, 8, 2.4, 13.6, 21, 13.6};
            default -> new double[]{-8, 8, -8, 24, 32, 24};
        };
        for (int axis = 0; axis < 3; axis++) {
            assertTrue(path, point[axis] >= bounds[axis] - .0001 && point[axis] <= bounds[axis + 3] + .0001);
        }
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
}
