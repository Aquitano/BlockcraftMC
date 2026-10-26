package me.blockcraft.api.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

/**
 * The pack's overrides of vanilla items, {@code assets/minecraft/items/<item>.json}: each selects on the
 * custom_model_data a {@link MenuIcon} or a {@link PackModel} puts on that item and otherwise draws the item exactly as
 * vanilla does. {@link #main} writes them from the code's icons and models, copying each item's vanilla definition out
 * of the Minecraft client jar as the fallback: {@code ./gradlew :paper:api:packOverrides -PclientJar=<jar>}.
 */
public final class PackOverrides {

    static final Path ITEMS = Path.of("assets/minecraft/items");

    private PackOverrides() {
    }

    /** Per vanilla item, the custom_model_data strings the pack answers and the model each draws. */
    static Map<String, Map<String, String>> cases() {
        var cases = new TreeMap<String, Map<String, String>>();
        for (var icon : MenuIcon.values()) {
            add(cases, icon.fallback(), icon.modelData(), "blockcraft:item/" + icon.key());
        }
        for (var model : PackModel.values()) {
            add(cases, model.fallback(), model.key(), model.model().asString());
        }
        return cases;
    }

    private static void add(Map<String, Map<String, String>> cases, Material item, String when, String model) {
        cases.computeIfAbsent(item.getKey().getKey(), ignored -> new LinkedHashMap<>()).put(when, model);
    }

    public static void main(String[] args) throws IOException {
        var pack = Path.of(args[1]);
        var directory = pack.resolve(ITEMS);
        Files.createDirectories(directory);
        var cases = cases();
        try (var stale = Files.list(directory)) {
            for (var file : stale.filter(file -> !cases.containsKey(file.getFileName().toString().replace(".json", ""))).toList()) {
                Files.delete(file);
            }
        }
        var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        try (var client = new ZipFile(args[0])) {
            for (var item : cases.entrySet()) {
                var entry = client.getEntry("assets/minecraft/items/" + item.getKey() + ".json");
                if (entry == null) {
                    throw new IllegalStateException("The client jar has no item " + item.getKey());
                }
                JsonObject vanilla;
                try (var reader = new InputStreamReader(client.getInputStream(entry), StandardCharsets.UTF_8)) {
                    vanilla = JsonParser.parseReader(reader).getAsJsonObject();
                }
                var override = vanilla.deepCopy();
                override.add("model", select(item.getValue(), vanilla.get("model").getAsJsonObject()));
                Files.writeString(directory.resolve(item.getKey() + ".json"), gson.toJson(override) + "\n");
            }
        }
        System.out.println("Wrote " + cases.size() + " overrides to " + directory);
    }

    private static JsonObject select(Map<String, String> cases, JsonObject fallback) {
        var list = new JsonArray();
        cases.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.naturalOrder())).forEach(entry -> {
            var model = new JsonObject();
            model.addProperty("type", "minecraft:model");
            model.addProperty("model", entry.getValue());
            var match = new JsonObject();
            match.addProperty("when", entry.getKey());
            match.add("model", model);
            list.add(match);
        });
        var select = new JsonObject();
        select.addProperty("type", "minecraft:select");
        select.addProperty("property", "minecraft:custom_model_data");
        select.addProperty("index", 0);
        select.add("cases", list);
        select.add("fallback", fallback);
        return select;
    }
}
