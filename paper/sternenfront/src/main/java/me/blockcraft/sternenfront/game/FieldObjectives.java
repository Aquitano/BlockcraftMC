package me.blockcraft.sternenfront.game;

import me.blockcraft.api.game.BlockChanges;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.scenery.FurnitureSolids;
import me.blockcraft.api.scenery.FurnitureUseEvent;
import me.blockcraft.api.world.MapSaveEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class FieldObjectives implements Listener {
    enum Kind { SHIELD_GENERATOR, POWER_RELAY, COMMUNICATIONS_CONSOLE }
    private record Circuit(Faction owner, List<String> doors, List<String> lights, PowerCircuit state) { }
    private static final class Device {
        final Kind kind;
        final Circuit circuit;
        final Location at;
        final List<Block> shield;
        boolean enabled = true;
        ItemDisplay display;
        String appearance = "";
        Device(Kind kind, Circuit circuit, Location at, List<Block> shield) {
            this.kind = kind; this.circuit = circuit; this.at = at; this.shield = shield;
        }
    }
    private record Channel(Player player, Device device, Faction faction, long started) { }
    private record ShieldBlock(BlockData before, BlockData placed) { }
    private record Target(ItemDisplay entity, boolean original, boolean door) { }
    private static final NamespacedKey SCENERY = new NamespacedKey("blockcraftapi", "scenery");
    private final SternenfrontGame game;
    private final Map<String, Circuit> circuits = new LinkedHashMap<>();
    private final List<Device> devices = new ArrayList<>();
    private final Map<UUID, Channel> channels = new HashMap<>();
    private final Map<Block, ShieldBlock> shields = new HashMap<>();
    private final Map<String, Target> targets = new HashMap<>();
    private final Map<String, Long> nextLookup = new HashMap<>();
    private final long channelTicks;
    private final long overrideTicks;

    FieldObjectives(SternenfrontGame game, Path file) {
        this.game = game;
        channelTicks = Math.max(1, Math.round(game.plugin().getConfig().getDouble("field-objectives.channel-seconds") * 20));
        overrideTicks = Math.max(1, game.plugin().getConfig().getLong("field-objectives.override-seconds") * 20);
        load(file);
    }

    /** Reads the devices of the game's map, which {@code file} belongs to, in place of any read before. */
    void load(Path file) {
        clear();
        circuits.clear();
        devices.clear();
        var yaml = YamlConfiguration.loadConfiguration(file.toFile());
        var listed = yaml.getConfigurationSection("circuits");
        if (listed != null) for (var id : listed.getKeys(false)) {
            var section = listed.getConfigurationSection(id);
            circuits.put(id, new Circuit(Faction.valueOf(section.getString("owner").toUpperCase(Locale.ROOT)),
                    section.getStringList("doors"), section.getStringList("lights"), new PowerCircuit()));
        }
        var sites = yaml.getConfigurationSection("devices");
        if (sites != null) for (var id : sites.getKeys(false)) {
            var section = sites.getConfigurationSection(id);
            var kind = Kind.valueOf(section.getString("kind").toUpperCase(Locale.ROOT));
            var circuit = circuits.get(section.getString("circuit"));
            if (circuit == null) throw new IllegalArgumentException("Unknown circuit for field objective " + id);
            var shield = section.getStringList("shield").stream().map(value -> spot(game.map().world(), value).getBlock()).toList();
            if (kind == Kind.SHIELD_GENERATOR && shield.isEmpty()) throw new IllegalArgumentException("Shield generator needs shield cells: " + id);
            devices.add(new Device(kind, circuit, spot(game.map().world(), section.getString("at")), shield));
        }
    }

    private static Location spot(World world, String value) {
        if (value == null) throw new IllegalArgumentException("Field objective needs a position");
        var parts = value.trim().split("\\s+");
        if (parts.length < 3 || parts.length > 4) throw new IllegalArgumentException("Field objective positions use x y z [yaw]");
        var at = new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                parts.length == 4 ? Float.parseFloat(parts[3]) : 180, 0);
        at.checkFinite();
        return at;
    }

    void start() {
        clear();
        circuits.values().forEach(circuit -> circuit.state().reset());
        devices.forEach(device -> device.enabled = true);
        refresh(0);
    }

    void tick(long tick) {
        if (devices.isEmpty() || tick % 4 != 0) return;
        for (var channel : List.copyOf(channels.values())) {
            var player = channel.player();
            var device = channel.device();
            if (!game.onField(player) || !player.isSneaking() || !player.getWorld().equals(device.at.getWorld())
                    || player.getLocation().distanceSquared(device.at) > 16
                    || game.factionOf(player).filter(channel.faction()::equals).isEmpty()) {
                channels.remove(player.getUniqueId());
                continue;
            }
            if (tick-channel.started() >= channelTicks) {
                boolean friendly = channel.faction() == device.circuit.owner();
                switch (device.kind) {
                    case POWER_RELAY -> device.circuit.state().power(friendly);
                    case COMMUNICATIONS_CONSOLE -> device.circuit.state().console(friendly, tick, overrideTicks);
                    case SHIELD_GENERATOR -> device.enabled = friendly;
                }
                channels.remove(player.getUniqueId());
                game.feedback().notice(player, game.messages().get("field-objectives.complete"));
            } else game.feedback().notice(player, game.messages().get("field-objectives.progress",
                    Formatter.number("progress", (tick-channel.started()) * 100 / channelTicks)));
        }
        refresh(tick);
    }

    private void refresh(long tick) {
        var wanted = new LinkedHashMap<Block, BlockData>();
        for (var device : devices) {
            boolean active = device.enabled && device.circuit.state().secure(tick);
            String state = active ? "active" : "disabled";
            visual(device, state);
            if (device.kind == Kind.SHIELD_GENERATOR && active) {
                var data = Material.valueOf(device.circuit.owner().blocks().name()+"_STAINED_GLASS").createBlockData();
                device.shield.forEach(block -> wanted.put(block, data));
            }
        }
        var changes = new LinkedHashMap<Block, BlockData>();
        for (var entry : List.copyOf(shields.entrySet())) if (!wanted.containsKey(entry.getKey())) {
            if (entry.getKey().getBlockData().matches(entry.getValue().placed())) changes.put(entry.getKey(), entry.getValue().before());
            shields.remove(entry.getKey());
        }
        for (var entry : wanted.entrySet()) {
            if (!shields.containsKey(entry.getKey()) && entry.getKey().getType().isAir()) {
                shields.put(entry.getKey(), new ShieldBlock(entry.getKey().getBlockData(), entry.getValue()));
                changes.put(entry.getKey(), entry.getValue());
            }
        }
        BlockChanges.set(changes);
        for (var circuit : circuits.values()) {
            circuit.lights().forEach(id -> target(id, false));
            circuit.doors().forEach(id -> target(id, true));
            for (var id : circuit.lights()) {
                var target = targets.get(id);
                if (target != null && FurnitureSolids.lit(target.entity()) != circuit.state().powered()) {
                    FurnitureSolids.power(target.entity(), circuit.state().powered());
                }
            }
            for (var id : circuit.doors()) {
                var target = targets.get(id);
                boolean open = !circuit.state().secure(tick);
                if (target != null && FurnitureSolids.open(target.entity()) != open) FurnitureSolids.door(target.entity(), open);
            }
        }
    }

    private void target(String id, boolean door) {
        var existing = targets.get(id);
        if (existing != null && existing.entity().isValid()) return;
        if (nextLookup.getOrDefault(id, 0L) > game.tick()) return;
        nextLookup.put(id, game.tick()+20);
        targets.remove(id);
        for (var entity : game.map().world().getEntitiesByClass(ItemDisplay.class)) {
            if (id.equals(entity.getPersistentDataContainer().get(SCENERY, PersistentDataType.STRING))) {
                targets.put(id, new Target(entity, door ? FurnitureSolids.open(entity) : FurnitureSolids.lit(entity), door));
                return;
            }
        }
    }

    private void visual(Device device, String state) {
        if (device.display != null && device.display.isValid() && device.appearance.equals(state)) return;
        if (device.display != null && device.display.isValid()) FurnitureSolids.remove(device.display);
        device.display = FurnitureSolids.place("blockcraft_objective_"+device.kind.name().toLowerCase(Locale.ROOT)+"_"+state,
                device.at, device.at.getYaw()).orElseThrow(() -> new IllegalStateException("Cannot place field objective at " + device.at));
        device.appearance = state;
        FurnitureSolids.protect(device.display);
    }

    boolean impact(Block block, Location at, org.bukkit.util.Vector normal) {
        if (!shields.containsKey(block)) return false;
        devices.stream().filter(device -> device.shield.contains(block)).findFirst()
                .ifPresent(device -> game.effects().shieldRipple(at, normal, device.circuit.owner().bolt()));
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void use(FurnitureUseEvent event) {
        if (game.phase() != GamePhase.INGAME || !game.onField(event.player())) return;
        var device = devices.stream().filter(site -> site.display != null && site.display.equals(event.entity())).findFirst();
        if (device.isEmpty()) return;
        event.setCancelled(true);
        var player = event.player();
        if (!player.isSneaking()) {
            game.feedback().notice(player, game.messages().get("field-objectives.hint"));
            return;
        }
        if (channels.values().stream().anyMatch(channel -> channel.device() == device.orElseThrow())) return;
        game.factionOf(player).ifPresent(faction -> channels.put(player.getUniqueId(), new Channel(player, device.orElseThrow(), faction, game.tick())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damaged(EntityDamageEvent event) {
        if (event.getFinalDamage() > 0) channels.remove(event.getEntity().getUniqueId());
    }

    @EventHandler public void quit(PlayerQuitEvent event) { channels.remove(event.getPlayer().getUniqueId()); }

    private void hide() {
        for (var entry : shields.entrySet()) {
            if (entry.getKey().getBlockData().matches(entry.getValue().placed())) BlockChanges.set(entry.getKey(), entry.getValue().before());
        }
        shields.clear();
        devices.forEach(device -> {
            if (device.display != null && device.display.isValid()) FurnitureSolids.remove(device.display);
            device.display = null;
        });
    }

    void clear() {
        channels.clear();
        hide();
        targets.values().stream().filter(target -> target.entity().isValid()).forEach(target -> {
            if (target.door()) FurnitureSolids.door(target.entity(), target.original());
            else FurnitureSolids.power(target.entity(), target.original());
        });
        targets.clear();
        nextLookup.clear();
    }

    @EventHandler public void save(MapSaveEvent event) {
        if (event.world().equals(game.map().world()) && game.phase() == GamePhase.INGAME) {
            event.suspend(this::hide, () -> refresh(game.tick()));
        }
    }
}
