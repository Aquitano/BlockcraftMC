package me.blockcraft.archive.exhibit;

import me.blockcraft.api.world.Point;
import me.blockcraft.archive.note.Note;
import me.blockcraft.common.player.PlayerReference;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * One old map on show, as its {@code exhibit.yml} describes it: what the menu calls it, which server and year it comes
 * from, where a visitor arrives, the checkpoints worth a jump, the figures standing in it and the notes it brings
 * along. Without an arrival a visitor lands on the world's own spawn.
 */
public record Exhibit(String key, String name, String origin, OptionalInt year, Optional<String> category, String description, Material icon,
                      Optional<Point> arrival, List<Checkpoint> checkpoints, List<Figure> figures, List<Note> notes) {

    private static final Material DEFAULT_ICON = Material.PAPER;

    public Exhibit {
        checkpoints = List.copyOf(checkpoints);
        figures = List.copyOf(figures);
        notes = List.copyOf(notes);
    }

    public static Exhibit load(String key, Path file) {
        return parse(key, YamlConfiguration.loadConfiguration(file.toFile()));
    }

    static Exhibit parse(String key, ConfigurationSection yaml) {
        var origin = yaml.getString("origin", "");
        if (origin.isBlank()) {
            throw new IllegalArgumentException("missing origin");
        }
        var icon = Material.matchMaterial(yaml.getString("icon", ""));
        var checkpoints = yaml.getMapList("checkpoints").stream().map(Exhibit::checkpoint).toList();
        var figures = yaml.getMapList("figures").stream().map(Exhibit::figure).toList();
        var notes = yaml.getMapList("notes");
        return new Exhibit(key, yaml.getString("name", key), origin,
                yaml.isInt("year") ? OptionalInt.of(yaml.getInt("year")) : OptionalInt.empty(),
                Optional.ofNullable(yaml.getString("category")).map(String::strip).filter(value -> !value.isEmpty()),
                yaml.getString("description", ""), icon == null ? DEFAULT_ICON : icon,
                Optional.ofNullable(yaml.getConfigurationSection("arrival")).map(section -> Point.parse(section.getValues(false))),
                checkpoints, figures, IntStream.range(0, notes.size()).mapToObj(index -> note(key, index, notes.get(index))).toList());
    }

    public void save(Path file) {
        try {
            yaml().save(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save " + file, e);
        }
    }

    YamlConfiguration yaml() {
        var yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("origin", origin);
        year.ifPresent(value -> yaml.set("year", value));
        category.ifPresent(value -> yaml.set("category", value));
        yaml.set("description", description);
        yaml.set("icon", icon.getKey().getKey());
        arrival.ifPresent(point -> yaml.set("arrival", point.serialize()));
        yaml.set("checkpoints", checkpoints.stream().map(Exhibit::serialize).toList());
        if (!figures.isEmpty()) {
            yaml.set("figures", figures.stream().map(Exhibit::serialize).toList());
        }
        if (!notes.isEmpty()) {
            yaml.set("notes", notes.stream().map(Exhibit::serialize).toList());
        }
        return yaml;
    }

    public Optional<Checkpoint> checkpoint(String name) {
        return checkpoints.stream().filter(checkpoint -> checkpoint.name().equalsIgnoreCase(name)).findFirst();
    }

    public Exhibit withArrival(Point value) {
        return new Exhibit(key, name, origin, year, category, description, icon, Optional.of(value), checkpoints, figures, notes);
    }

    /** Adds the checkpoint, or puts it in the place of the one with its name. */
    public Exhibit withCheckpoint(Checkpoint value) {
        var changed = new ArrayList<Checkpoint>();
        boolean replaced = false;
        for (var checkpoint : checkpoints) {
            if (checkpoint.name().equalsIgnoreCase(value.name())) {
                changed.add(value);
                replaced = true;
            } else {
                changed.add(checkpoint);
            }
        }
        if (!replaced) {
            changed.add(value);
        }
        return new Exhibit(key, name, origin, year, category, description, icon, arrival, changed, figures, notes);
    }

    public Exhibit withoutCheckpoint(String name) {
        return new Exhibit(key, this.name, origin, year, category, description, icon, arrival,
                checkpoints.stream().filter(checkpoint -> !checkpoint.name().equalsIgnoreCase(name)).toList(), figures, notes);
    }

    private static Checkpoint checkpoint(Map<?, ?> values) {
        if (!(values.get("name") instanceof String name) || !(values.get("at") instanceof Map<?, ?> at)) {
            throw new IllegalArgumentException("a checkpoint needs a name and 'at': " + values);
        }
        var description = values.get("description") instanceof String text ? text : "";
        return new Checkpoint(name, description, Point.parse(at));
    }

    private static Figure figure(Map<?, ?> values) {
        if (!(values.get("name") instanceof String name) || !(values.get("at") instanceof Map<?, ?> at)
                || !(values.get("skin") instanceof String skin) || !(values.get("signature") instanceof String signature)) {
            throw new IllegalArgumentException("a figure needs a name, 'at', a skin and its signature: " + values.get("name"));
        }
        return new Figure(name, Point.parse(at), skin, signature);
    }

    /**
     * A note that comes with the map instead of the database, so it survives any server; it reads and travels like one
     * staff left. Its id is negative and stands for its place in the file, so it never meets the id of a stored note.
     */
    private static Note note(String key, int index, Map<?, ?> values) {
        if (!(values.get("text") instanceof String text) || !(values.get("at") instanceof Map<?, ?> at)
                || !(values.get("author") instanceof String author)) {
            throw new IllegalArgumentException("a note needs a text, 'at', an author and a date: " + values.get("text"));
        }
        return new Note(-(index + 1L), key, Point.parse(at), new PlayerReference(author, Optional.empty()), text, written(values.get("date")));
    }

    /** YAML reads an unquoted 2026-10-31 as a timestamp and a quoted one as text; both mean that day. */
    private static Instant written(Object date) {
        return switch (date) {
            case Date timestamp -> timestamp.toInstant();
            case String day -> LocalDate.parse(day).atStartOfDay(ZoneOffset.UTC).toInstant();
            case null, default -> throw new IllegalArgumentException("a note needs a date like 2026-10-31, not " + date);
        };
    }

    private static Map<String, Object> serialize(Note note) {
        var values = new LinkedHashMap<String, Object>();
        values.put("text", note.text());
        values.put("at", note.at().serialize());
        values.put("author", note.author().name());
        values.put("date", LocalDate.ofInstant(note.createdAt(), ZoneOffset.UTC).toString());
        return values;
    }

    private static Map<String, Object> serialize(Figure figure) {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", figure.name());
        values.put("at", figure.at().serialize());
        values.put("skin", figure.skin());
        values.put("signature", figure.signature());
        return values;
    }

    private static Map<String, Object> serialize(Checkpoint checkpoint) {
        var values = new LinkedHashMap<String, Object>();
        values.put("name", checkpoint.name());
        values.put("description", checkpoint.description());
        values.put("at", checkpoint.at().serialize());
        return values;
    }
}
