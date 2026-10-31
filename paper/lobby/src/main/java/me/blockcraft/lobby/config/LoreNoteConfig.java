package me.blockcraft.lobby.config;

import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lobby's {@code lore-notes} section: what the first read of a note pays, and the notes in the order the secrets
 * menu lists them, each with the item it lies as (a book, a sheet of paper, a map).
 */
public record LoreNoteConfig(int reward, Map<String, Material> notes) {

    public static LoreNoteConfig fromYaml(Map<String, Object> config) {
        var notes = new LinkedHashMap<String, Material>();
        if (config.get("notes") instanceof Map<?, ?> listed) {
            listed.forEach((key, item) -> {
                var material = Material.matchMaterial(String.valueOf(item).toUpperCase(Locale.ROOT));
                notes.put(String.valueOf(key), material == null || !material.isItem() ? Material.PAPER : material);
            });
        }
        return new LoreNoteConfig(Math.max(0, config.get("reward") instanceof Number number ? number.intValue() : 0),
                Collections.unmodifiableMap(notes));
    }
}
