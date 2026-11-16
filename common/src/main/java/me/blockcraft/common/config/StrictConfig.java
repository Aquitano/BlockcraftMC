package me.blockcraft.common.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A config whose bundled file holds every key, so a read carries no fallback of its own. A missing key or one of the
 * wrong type throws with its dotted path; read at startup, that stops a broken override before it turns into a silent
 * default. Keys may be dotted to reach into nested sections.
 *
 * @param path the dotted path of {@code values} in the file, empty at its root
 */
public record StrictConfig(String file, String path, Map<String, Object> values) {

    public static StrictConfig of(String file, Map<String, Object> root) {
        return new StrictConfig(file, "", root);
    }

    /** YAML may key a nested section by numbers, which {@link #keys} still reads as text. */
    @SuppressWarnings("unchecked")
    public StrictConfig section(String key) {
        if (value(key) instanceof Map<?, ?> section) {
            return new StrictConfig(file, where(key), (Map<String, Object>) section);
        }
        throw wrong(key, "a section");
    }

    public Set<String> keys() {
        var keys = new LinkedHashSet<String>();
        values.keySet().forEach(key -> keys.add(String.valueOf(key)));
        return keys;
    }

    public boolean has(String key) {
        return find(key) != null;
    }

    public int integer(String key) {
        if (value(key) instanceof Number number && number.doubleValue() == number.intValue()) {
            return number.intValue();
        }
        throw wrong(key, "a whole number");
    }

    public double decimal(String key) {
        if (value(key) instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        throw wrong(key, "a finite number");
    }

    public boolean flag(String key) {
        if (value(key) instanceof Boolean flag) {
            return flag;
        }
        throw wrong(key, "true or false");
    }

    /** Numbers and booleans read as their text, as Bukkit's {@code getString} does. */
    public String text(String key) {
        var value = value(key);
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw wrong(key, "a text");
    }

    public List<String> texts(String key) {
        if (value(key) instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        throw wrong(key, "a list");
    }

    public List<Integer> integers(String key) {
        if (value(key) instanceof List<?> list && list.stream().allMatch(Integer.class::isInstance)) {
            return list.stream().map(Integer.class::cast).toList();
        }
        throw wrong(key, "a list of whole numbers");
    }

    private Object value(String key) {
        var value = find(key);
        if (value == null) {
            throw new IllegalStateException(file + ": " + where(key) + " is missing");
        }
        return value;
    }

    private Object find(String key) {
        Object current = values;
        for (var part : key.split("\\.")) {
            if (!(current instanceof Map<?, ?> section)) {
                return null;
            }
            current = section.get(part);
        }
        return current;
    }

    private IllegalStateException wrong(String key, String expected) {
        return new IllegalStateException(file + ": " + where(key) + " must be " + expected + ", not " + find(key));
    }

    private String where(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }
}
