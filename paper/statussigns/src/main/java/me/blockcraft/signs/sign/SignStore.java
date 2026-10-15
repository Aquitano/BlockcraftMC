package me.blockcraft.signs.sign;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/** Persists signs in {@code signs.yml} as {@code signs.<id>: {group, loc: {world, x, y, z}}}. */
public final class SignStore {

    private static final String ROOT = "signs";

    private final Path file;
    private final Logger logger;
    private final YamlConfiguration yaml;

    public SignStore(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.yaml = YamlConfiguration.loadConfiguration(file.toFile());
    }

    public List<StatusSign> load() {
        var signs = new ArrayList<StatusSign>();
        ConfigurationSection root = yaml.getConfigurationSection(ROOT);
        if (root == null) {
            return signs;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection entry = root.getConfigurationSection(key);
            if (entry == null || !entry.isConfigurationSection("loc") || !entry.isString("group")) {
                logger.warning("Dropping invalid status sign entry " + key);
                root.set(key, null);
                continue;
            }
            read(entry).ifPresentOrElse(signs::add,
                    () -> logger.warning("Skipping status sign " + key + ": world " + entry.getString("loc.world") + " is not loaded"));
        }
        save();
        return signs;
    }

    public void add(StatusSign sign) {
        ConfigurationSection root = yaml.getConfigurationSection(ROOT);
        if (root == null) {
            root = yaml.createSection(ROOT);
        }
        int nextId = root.getKeys(false).stream().mapToInt(SignStore::parseId).max().orElse(0) + 1;
        ConfigurationSection entry = root.createSection(String.valueOf(nextId));
        entry.set("group", sign.group());
        ConfigurationSection loc = entry.createSection("loc");
        loc.set("world", sign.location().getWorld().getName());
        loc.set("x", sign.location().getBlockX());
        loc.set("y", sign.location().getBlockY());
        loc.set("z", sign.location().getBlockZ());
        save();
    }

    /** Empty while the sign's world is not loaded. */
    private static Optional<StatusSign> read(ConfigurationSection entry) {
        ConfigurationSection loc = entry.getConfigurationSection("loc");
        var world = Bukkit.getWorld(String.valueOf(loc.getString("world")));
        if (world == null) {
            return Optional.empty();
        }
        var location = new Location(world, loc.getInt("x"), loc.getInt("y"), loc.getInt("z"));
        return Optional.of(new StatusSign(location, entry.getString("group")));
    }

    private static int parseId(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void save() {
        try {
            yaml.save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }
}
