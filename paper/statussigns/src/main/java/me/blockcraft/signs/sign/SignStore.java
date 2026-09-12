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

/** Persists signs in {@code signs.yml} as {@code signs.<id>: {name, ip, port, loc: {world, x, y, z}}}. */
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
            Optional<StatusSign> sign = entry == null ? Optional.empty() : read(entry);
            if (sign.isPresent()) {
                signs.add(sign.get());
            } else {
                logger.warning("Dropping invalid status sign entry " + key);
                root.set(key, null);
            }
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
        entry.set("name", sign.name());
        entry.set("ip", sign.host());
        entry.set("port", sign.port());
        ConfigurationSection loc = entry.createSection("loc");
        loc.set("world", sign.location().getWorld().getName());
        loc.set("x", sign.location().getBlockX());
        loc.set("y", sign.location().getBlockY());
        loc.set("z", sign.location().getBlockZ());
        save();
    }

    private static Optional<StatusSign> read(ConfigurationSection entry) {
        ConfigurationSection loc = entry.getConfigurationSection("loc");
        String name = entry.getString("name");
        String host = entry.getString("ip");
        if (loc == null || name == null || host == null) {
            return Optional.empty();
        }
        var world = Bukkit.getWorld(String.valueOf(loc.getString("world")));
        if (world == null) {
            return Optional.empty();
        }
        var location = new Location(world, loc.getInt("x"), loc.getInt("y"), loc.getInt("z"));
        return Optional.of(new StatusSign(location, name, host, entry.getInt("port")));
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
