package me.blockcraft.zombies.arena;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ArenaMap {

    public record Room(String id, BlockBox bounds, BlockBox door, int price) { }
    public record Window(String id, String room, BlockBox boards, Location spawn, Location entry, Optional<BlockBox> bay) { }
    public record Station(String id, String room, StationKind kind, Location location) { }

    private static final String DISPLAY_TAG = "blockcraft_zombies_display";
    private final Path file;
    private final World world;
    private final YamlConfiguration yaml;
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final List<Window> windows = new ArrayList<>();
    private final List<Station> stations = new ArrayList<>();
    private final Set<String> unlocked = new HashSet<>(Set.of("yard"));
    private final List<BlockState> originals = new ArrayList<>();
    private final List<TextDisplay> labels = new ArrayList<>();
    private record DoorLabel(TextDisplay display, net.kyori.adventure.text.Component opened) { }
    private final Map<String, DoorLabel> doorLabels = new LinkedHashMap<>();
    private Plugin chunkOwner;
    private org.bukkit.block.data.BlockData windowData;

    public ArenaMap(Path file, World world) {
        this.file = file;
        this.world = world;
        this.yaml = YamlConfiguration.loadConfiguration(file.toFile());
        for (String id : section("rooms").getKeys(false)) {
            rooms.put(id, new Room(id, box("rooms." + id + ".bounds"), box("rooms." + id + ".door"),
                    Math.max(0, yaml.getInt("rooms." + id + ".price"))));
        }
        for (String id : section("windows").getKeys(false)) {
            String path = "windows." + id + ".";
            windows.add(new Window(id, yaml.getString(path + "room", "yard"), box(path + "boards"), point(path + "spawn"), point(path + "entry"),
                    yaml.isConfigurationSection(path + "bay") ? Optional.of(box(path + "bay")) : Optional.empty()));
        }
        for (String id : section("stations").getKeys(false)) {
            String path = "stations." + id + ".";
            stations.add(new Station(id, yaml.getString(path + "room", "yard"),
                    StationKind.valueOf(yaml.getString(path + "kind")), point(path + "location")));
        }
    }

    public World world() { return world; }
    public String name() { return yaml.getString("name", world.getName()); }
    public Location point(String key) {
        var origin = section("origin");
        var point = section(key);
        return new Location(world, origin.getInt("x") + point.getDouble("x"), origin.getInt("y") + point.getDouble("y"),
                origin.getInt("z") + point.getDouble("z"), (float) point.getDouble("yaw"), (float) point.getDouble("pitch"));
    }

    public void setPoint(String key, Location location) {
        if (!location.getWorld().equals(world)) {
            return;
        }
        var origin = section("origin");
        yaml.set(key + ".x", location.getX() - origin.getInt("x"));
        yaml.set(key + ".y", location.getY() - origin.getInt("y"));
        yaml.set(key + ".z", location.getZ() - origin.getInt("z"));
        yaml.set(key + ".yaw", location.getYaw());
        yaml.set(key + ".pitch", location.getPitch());
        save();
    }

    public List<Window> windows() { return List.copyOf(windows); }
    public List<Station> stations() { return List.copyOf(stations); }
    public List<Room> rooms() { return List.copyOf(rooms.values()); }
    public boolean unlocked(String room) { return unlocked.contains(room); }
    public void unlock(String room) { unlocked.add(room); }

    public Optional<Room> roomAt(Location location) {
        if (!location.getWorld().equals(world)) {
            return Optional.empty();
        }
        return rooms.values().stream().filter(room -> room.bounds().contains(location)).findFirst();
    }

    public Optional<Room> doorAt(Block block) {
        return rooms.values().stream().filter(room -> !room.id().equals("yard") && room.door().contains(block.getLocation())).findFirst();
    }

    public Optional<Station> stationAt(Block block) {
        return stations.stream().filter(station -> station.location().getBlock().equals(block)).findFirst();
    }

    public boolean enterable(Location location) {
        return roomAt(location).map(room -> unlocked(room.id())).orElse(false);
    }

    public List<Window> activeWindows() {
        return windows.stream().filter(window -> unlocked(window.room())).toList();
    }

    public void keepLoaded(Plugin plugin) {
        chunkOwner = plugin;
        for (var room : rooms.values()) {
            for (int x = room.bounds().minX() >> 4; x <= room.bounds().maxX() >> 4; x++) {
                for (int z = room.bounds().minZ() >> 4; z <= room.bounds().maxZ() >> 4; z++) {
                    world.addPluginChunkTicket(x, z, plugin);
                }
            }
        }
        windows.forEach(window -> world.addPluginChunkTicket(window.spawn().getBlockX() >> 4, window.spawn().getBlockZ() >> 4, plugin));
    }

    public boolean prepare() {
        if (!validLayout()) {
            return false;
        }
        if (yaml.getBoolean("starter.build") && !yaml.getBoolean("starter.built")) {
            var origin = section("origin");
            var bounds = new BlockBox(origin.getInt("x") - 29, origin.getInt("y") - 1, origin.getInt("z") - 29,
                    origin.getInt("x") + 29, origin.getInt("y") + 9, origin.getInt("z") + 29);
            boolean contained = rooms.values().stream().allMatch(room -> bounds.contains(room.bounds()) && bounds.contains(room.door()))
                    && windows.stream().allMatch(window -> bounds.contains(window.boards()) && window.bay().map(bounds::contains).orElse(true))
                    && stations.stream().allMatch(station -> bounds.contains(station.location().clone().add(0, -1, 0))
                            && bounds.contains(station.location().clone().add(0, 5, 0)));
            if (!contained || bounds.minY() < world.getMinHeight() || bounds.maxY() >= world.getMaxHeight()
                    || bounds.blocks(world).stream().anyMatch(block -> !block.getType().isAir())) {
                return false;
            }
            StarterArena.build(this, bounds);
            yaml.set("starter.built", true);
            save();
        }
        var doorData = org.bukkit.Bukkit.createBlockData(yaml.getString("door-block-data", "minecraft:waxed_cut_copper"));
        windowData = org.bukkit.Bukkit.createBlockData(yaml.getString("window-block-data", "minecraft:oak_slab[type=top,waterlogged=false]"));
        for (var room : rooms.values()) {
            if (!room.id().equals("yard")) {
                room.door().blocks(world).forEach(block -> {
                    block.setBlockData(doorData, false);
                    originals.add(block.getState());
                });
            }
        }
        windows.forEach(window -> window.boards().blocks(world).forEach(block -> {
            block.setBlockData(windowData, false);
            originals.add(block.getState());
        }));
        world.setDifficulty(org.bukkit.Difficulty.valueOf(yaml.getString("difficulty", "NORMAL")));
        world.setGameRule(org.bukkit.GameRules.NATURAL_HEALTH_REGENERATION, false);
        me.blockcraft.api.world.Worlds.setTime(world, yaml.getLong("time", 18000));
        return ready();
    }

    private boolean validLayout() {
        return rooms.containsKey("yard") && windows.stream().anyMatch(window -> window.room().equals("yard"))
                && rooms.values().stream().allMatch(room -> room.id().equals("yard") || room.door().blocks(world).size() <= 64)
                && windows.stream().allMatch(window -> rooms.containsKey(window.room())
                        && rooms.get(window.room()).bounds().contains(window.entry())
                        && window.bay().map(bay -> bay.contains(window.spawn()) && !bay.contains(window.entry())).orElse(true))
                && stations.stream().allMatch(station -> rooms.containsKey(station.room())
                        && rooms.get(station.room()).bounds().contains(station.location()));
    }

    public boolean ready() {
        return validLayout() && enterable(point("spawn")) && point("spawn").getBlock().isPassable()
                && point("spawn").clone().add(0, 1, 0).getBlock().isPassable()
                && point("spawn").clone().add(0, -1, 0).getBlock().getType().isSolid();
    }

    public void open(Room room) {
        unlocked.add(room.id());
        room.door().blocks(world).forEach(block -> block.setType(Material.AIR, false));
        var label = doorLabels.get(room.id());
        if (label != null) {
            label.display().text(label.opened());
        }
    }

    public Optional<Block> repair(Window window) {
        return window.boards().blocks(world).stream().filter(block -> block.getType().isAir()).findFirst().map(block -> {
            block.setBlockData(windowData, false);
            return block;
        });
    }

    public void labels(Messages messages) {
        world.getEntitiesByClass(TextDisplay.class).stream().filter(entity -> entity.getScoreboardTags().contains(DISPLAY_TAG)).forEach(TextDisplay::remove);
        for (var station : stations) {
            label(station.location().clone().add(0.5, 1.5, 0.5), messages.get("station.label",
                    Placeholder.component("station", messages.get("station." + station.kind().key()))));
        }
        rooms.values().stream().filter(room -> !room.id().equals("yard")).forEach(room -> {
            var location = new Location(world, (room.door().minX() + room.door().maxX() + 1) / 2.0,
                    room.door().maxY() + 1.0, (room.door().minZ() + room.door().maxZ() + 1) / 2.0);
            var towardYard = point("spawn").toVector().subtract(location.toVector()).setY(0);
            if (towardYard.lengthSquared() > 0) {
                location.add(towardYard.normalize().multiply(0.65));
            }
            var roomName = Placeholder.component("room", messages.get("room." + room.id()));
            var display = label(location, messages.get("room.label", roomName,
                    net.kyori.adventure.text.minimessage.tag.resolver.Formatter.number("price", room.price())));
            doorLabels.put(room.id(), new DoorLabel(display, messages.get("room.open-label", roomName)));
        });
    }

    private TextDisplay label(Location location, net.kyori.adventure.text.Component text) {
        var display = world.spawn(location, TextDisplay.class, entity -> {
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setPersistent(false);
            entity.setSeeThrough(false);
            entity.setShadowed(true);
            entity.setViewRange(0.4f);
            entity.addScoreboardTag(DISPLAY_TAG);
        });
        labels.add(display);
        return display;
    }

    public void restore() {
        labels.forEach(TextDisplay::remove);
        restoreBlocks();
        originals.clear();
        if (chunkOwner != null) {
            world.removePluginChunkTickets(chunkOwner);
        }
    }

    public List<BlockState> snapshot() {
        return originals.stream().map(state -> state.getBlock().getState()).toList();
    }

    public void restoreBlocks() {
        originals.forEach(state -> state.update(true, false));
    }

    private BlockBox box(String path) {
        var origin = section("origin");
        var box = section(path);
        return new BlockBox(origin.getInt("x") + box.getInt("x1"), origin.getInt("y") + box.getInt("y1"),
                origin.getInt("z") + box.getInt("z1"), origin.getInt("x") + box.getInt("x2"),
                origin.getInt("y") + box.getInt("y2"), origin.getInt("z") + box.getInt("z2"));
    }

    private ConfigurationSection section(String key) {
        var section = yaml.getConfigurationSection(key);
        if (section == null) {
            throw new IllegalArgumentException("Missing arena section: " + key);
        }
        return section;
    }

    private void save() {
        try {
            yaml.save(file.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not save Zombies arena", exception);
        }
    }
}
