package me.blockcraft.common.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Supplier;

public final class YamlFile {

    private YamlFile() {
    }

    /**
     * Copies the bundled default to {@code target} if it does not exist yet, then loads the file with every key it
     * lacks taken from the bundled default, so a plugin update never runs on missing settings. Sections merge
     * key by key; lists and scalars in the file win as a whole.
     */
    public static Map<String, Object> loadWithDefaults(Path target, Supplier<InputStream> bundledDefault) {
        try {
            if (Files.notExists(target)) {
                Files.createDirectories(target.getParent());
                try (InputStream in = bundledDefault.get()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Map<String, Object> defaults;
            try (InputStream in = bundledDefault.get()) {
                defaults = load(in);
            }
            try (InputStream in = Files.newInputStream(target)) {
                return merge(defaults, load(in));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + target, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> merge(Map<String, Object> defaults, Map<String, Object> overrides) {
        var merged = new LinkedHashMap<>(defaults);
        overrides.forEach((key, value) -> merged.merge(key, value, (fallback, override) ->
                fallback instanceof Map<?, ?> && override instanceof Map<?, ?>
                        ? merge((Map<String, Object>) fallback, (Map<String, Object>) override)
                        : override));
        return merged;
    }

    public static Map<String, Object> load(InputStream in) {
        Map<String, Object> loaded = new Yaml().load(in);
        return loaded == null ? new LinkedHashMap<>() : loaded;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> section(Map<String, Object> root, String key) {
        return root.get(key) instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    /** Flattens nested maps into dotted keys: {a: {b: 1}} becomes {"a.b": "1"}; lists join with newlines. */
    public static Map<String, String> flatten(Map<String, Object> root) {
        var flat = new LinkedHashMap<String, String>();
        flattenInto(flat, "", root);
        return flat;
    }

    private static void flattenInto(Map<String, String> target, String prefix, Map<?, ?> source) {
        source.forEach((key, value) -> {
            String path = prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key;
            if (value instanceof Map<?, ?> nested) {
                flattenInto(target, path, nested);
            } else if (value instanceof List<?> lines) {
                target.put(path, lines.stream().map(String::valueOf).collect(Collectors.joining("\n")));
            } else if (value != null) {
                target.put(path, String.valueOf(value));
            }
        });
    }
}
