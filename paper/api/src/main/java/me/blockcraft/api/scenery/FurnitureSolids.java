package me.blockcraft.api.scenery;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurniturePlaceEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent;
import com.nexomc.nexo.mechanics.furniture.door.FurnitureDoor;
import me.blockcraft.api.game.BlockChanges;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

public final class FurnitureSolids implements Listener, AutoCloseable {
    private static final NamespacedKey COLLIDER = new NamespacedKey("blockcraftapi", "furniture_collider");
    private static final NamespacedKey ANCHOR = new NamespacedKey("blockcraftapi", "furniture_collider_anchor");
    private static final NamespacedKey SNAPSHOT = new NamespacedKey("blockcraftapi", "furniture_blocks");
    private static final NamespacedKey PROTECTED = new NamespacedKey("blockcraftapi", "protected_furniture");
    private record Lease(BlockData before, Map<UUID, BlockData> owners) {
        BlockData placed() {
            var data = effective(owners.values());
            return before instanceof Light original && data instanceof Light light && original.getLevel() > light.getLevel() ? before : data;
        }
    }
    private record Placed(ItemDisplay entity, List<Block> blocks, List<Shulker> colliders, List<ItemDisplay> anchors) {}
    private final Plugin plugin;
    private final Map<UUID, Placed> placed = new HashMap<>();
    private final Map<Block, Lease> leases = new HashMap<>();
    private final Set<UUID> doors = new HashSet<>();
    private final BukkitTask task;
    private boolean closed;

    public FurnitureSolids(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getWorlds().forEach(world -> world.getEntities().forEach(entity -> {
            if (entity.getPersistentDataContainer().has(COLLIDER) || entity.getPersistentDataContainer().has(ANCHOR)) entity.remove();
            else if (entity instanceof ItemDisplay display) later(display);
        }));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (var id : List.copyOf(doors)) {
                var entry = placed.get(id);
                if (entry == null) { doors.remove(id); continue; }
                if (!entry.entity().isValid()) remove(entry.entity().getUniqueId());
                else {
                    var mechanic = NexoFurniture.furnitureMechanic(entry.entity());
                    if (mechanic != null && mechanic.getDoor() != null) syncDoor(entry);
                }
            }
        }, 1, 2);
    }

    private void later(ItemDisplay display) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!closed && display.isValid() && !placed.containsKey(display.getUniqueId())) attach(display);
        });
    }

    private Map<Block, BlockData> desired(ItemDisplay entity) {
        var result = solidBlocks(entity, false);
        var mechanic = NexoFurniture.furnitureMechanic(entity);
        if (mechanic == null || mechanic.getLight() == null || !NexoFurniture.lightState(entity)) return result;
        var lights = mechanic.getLight().getLightBlocks();
        var locations = mechanic.getLight().lightBlockLocations(entity.getLocation(), entity.getYaw());
        for (int index = 0; index < lights.size(); index++) {
            var center = locations.get(index).getBlock();
            var choices = List.of(center, center.getRelative(0,1,0), center.getRelative(0,0,1),
                    center.getRelative(0,0,-1), center.getRelative(1,0,0), center.getRelative(-1,0,0));
            for (var block : choices) {
                if (result.containsKey(block) && result.get(block).getMaterial() != Material.LIGHT) continue;
                if (!block.getType().isAir() && block.getType() != Material.LIGHT) continue;
                var light = (Light) lights.get(index).getLightData().clone();
                var old = result.get(block);
                result.put(block, old instanceof Light existing && existing.getLevel() > light.getLevel() ? old : light);
                break;
            }
        }
        return result;
    }

    private Map<Block, BlockData> solidBlocks(ItemDisplay entity, boolean closedDoor) {
        var mechanic = NexoFurniture.furnitureMechanic(entity);
        var result = new LinkedHashMap<Block, BlockData>();
        if (mechanic == null || !closedDoor && mechanic.getDoor() != null && FurnitureDoor.Companion.isOpen(entity)) return result;
        if (!mechanic.getSection().isConfigurationSection("blockcraft_collision") && mechanic.getItemID().startsWith("blockcraft_")) {
            for (var at : mechanic.getHitbox().barrierLocations(entity.getLocation(),entity.getYaw())) result.put(at.getBlock(), Material.BARRIER.createBlockData());
            return result;
        }
        var origin = origin(entity);
        boolean grid = mechanic.getSection().getBoolean("blockcraft_collision.grid");
        if (grid) {
            origin.setX(Math.rint(origin.getX()));
            origin.setY(Math.rint(origin.getY()));
            origin.setZ(Math.rint(origin.getZ()));
        }
        for (var value : mechanic.getSection().getStringList("blockcraft_collision.blocks")) {
            var parts = value.split(" ", 2);
            var offset = vector(parts[0]);
            if (grid) offset.add(new Vector(.5,0,.5));
            var at = origin.clone().add(rotate(offset, entity.getYaw()+180));
            var block = at.getBlock();
            boolean keep = mechanic.getSection().getBoolean("blockcraft_collision.keep_existing");
            var data = Bukkit.createBlockData(parts[1]);
            data.rotate(switch (Math.floorMod(Math.round((entity.getYaw()+180)/90), 4)) {
                case 1 -> StructureRotation.CLOCKWISE_90;
                case 2 -> StructureRotation.CLOCKWISE_180;
                case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
                default -> StructureRotation.NONE;
            });
            result.put(block, keep && !block.isPassable() && !leases.containsKey(block)
                    ? block.getBlockData() : data);
        }
        return result;
    }

    private Location origin(ItemDisplay entity) {
        var section = NexoFurniture.furnitureMechanic(entity).getSection();
        var shift = vector(section.getString("blockcraft_collision.model_shift", "0,0,0"));
        return entity.getLocation().getBlock().getLocation().add(.5,0,.5).add(rotate(shift,entity.getYaw()));
    }

    private static Vector vector(String value) {
        var parts = value.split(",");
        if (parts.length != 3) throw new IllegalArgumentException("Furniture collision needs three coordinates");
        var point = new Vector(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        point.checkFinite();
        return point;
    }

    static Vector rotate(Vector point, float yaw) {
        int quarter = Math.floorMod(Math.round(yaw / 90), 4);
        return switch (quarter) {
            case 1 -> new Vector(-point.getZ(), point.getY(), point.getX());
            case 2 -> new Vector(-point.getX(), point.getY(), -point.getZ());
            case 3 -> new Vector(point.getZ(), point.getY(), -point.getX());
            default -> point.clone();
        };
    }

    private boolean available(Map<Block, BlockData> blocks) {
        return blocks.entrySet().stream().allMatch(entry -> entry.getKey().getType().isAir()
                || entry.getKey().getType() == Material.LIGHT
                || entry.getKey().getBlockData().matches(entry.getValue()));
    }

    static BlockData effective(Collection<BlockData> requested) {
        return requested.stream().filter(data -> data.getMaterial() != Material.LIGHT).findFirst()
                .orElseGet(() -> requested.stream().max(java.util.Comparator.comparingInt(data -> ((Light) data).getLevel())).orElseThrow());
    }

    private boolean apply(Placed entry, Map<Block, BlockData> wanted) {
        if (!available(wanted)) return false;
        var changed = new LinkedHashMap<Block, BlockData>();
        for (var block : List.copyOf(entry.blocks())) {
            if (!wanted.containsKey(block)) release(entry.entity().getUniqueId(), block, changed);
        }
        entry.blocks().clear();
        for (var value : wanted.entrySet()) {
            var lease = leases.computeIfAbsent(value.getKey(), block ->
                    new Lease(block.getBlockData().clone(), new HashMap<>()));
            lease.owners().put(entry.entity().getUniqueId(), value.getValue());
            if (!value.getKey().getBlockData().matches(lease.placed())) changed.put(value.getKey(), lease.placed());
            entry.blocks().add(value.getKey());
        }
        BlockChanges.set(changed);
        persist(entry);
        return true;
    }

    private void persist(Placed entry) {
        var values = new ArrayList<String>();
        for (var block : entry.blocks()) {
            var lease = leases.get(block);
            values.add(block.getX()+" "+block.getY()+" "+block.getZ()+" "+lease.before().getAsString()+" "+lease.owners().get(entry.entity().getUniqueId()).getAsString());
        }
        entry.entity().getPersistentDataContainer().set(SNAPSHOT, PersistentDataType.STRING, String.join("\n", values));
    }

    private List<Block> recover(ItemDisplay entity) {
        var blocks = new ArrayList<Block>();
        var snapshot = entity.getPersistentDataContainer().getOrDefault(SNAPSHOT, PersistentDataType.STRING, "");
        for (var line : snapshot.lines().toList()) {
            var parts = line.split(" ", 5);
            var block = entity.getWorld().getBlockAt(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            var lease = leases.computeIfAbsent(block, key -> new Lease(Bukkit.createBlockData(parts[3]), new HashMap<>()));
            lease.owners().put(entity.getUniqueId(), Bukkit.createBlockData(parts[4]));
            blocks.add(block);
        }
        return blocks;
    }

    private void attach(ItemDisplay entity) {
        var mechanic = NexoFurniture.furnitureMechanic(entity);
        if (mechanic == null || !mechanic.getItemID().startsWith("blockcraft_")
                || !mechanic.getSection().isConfigurationSection("blockcraft_collision") && mechanic.getHitbox().getBarriers().isEmpty()
                && (mechanic.getLight() == null || mechanic.getLight().isEmpty())) return;
        var entry = new Placed(entity, recover(entity), new ArrayList<>(), new ArrayList<>());
        if (!apply(entry, desired(entity))) {
            plugin.getSLF4JLogger().warn("Cannot place solid furniture {} at {}: occupied blocks", mechanic.getItemID(), entity.getLocation());
            NexoFurniture.remove(entity);
            return;
        }
        placed.put(entity.getUniqueId(), entry);
        if (mechanic.getDoor() != null) doors.add(entity.getUniqueId());
        var origin = origin(entity);
        for (var value : mechanic.getSection().getStringList("blockcraft_collision.cubes")) {
            var parts = value.split(" ", 2);
            var offset = rotate(vector(parts[0]), entity.getYaw()+180);
            double size = Double.parseDouble(parts[1]);
            if (!Double.isFinite(size) || size < .125 || size > 3) throw new IllegalArgumentException("Invalid solid furniture cube size");
            var at = origin.clone().add(offset);
            var anchor = entity.getWorld().spawn(at, ItemDisplay.class, display -> {
                display.setPersistent(false);
                display.getPersistentDataContainer().set(ANCHOR, PersistentDataType.STRING, entity.getUniqueId().toString());
            });
            var collider = entity.getWorld().spawn(at, Shulker.class, shulker -> {
                shulker.setAI(false);
                shulker.setGravity(false);
                shulker.setInvisible(true);
                shulker.setInvulnerable(true);
                shulker.setSilent(true);
                shulker.setPersistent(false);
                shulker.setRemoveWhenFarAway(false);
                shulker.setCollidable(true);
                shulker.setPeek(0);
                shulker.getAttribute(Attribute.SCALE).setBaseValue(size);
                shulker.getPersistentDataContainer().set(COLLIDER, PersistentDataType.STRING, entity.getUniqueId().toString());
            });
            anchor.addPassenger(collider);
            entry.colliders().add(collider);
            entry.anchors().add(anchor);
        }
    }

    private void release(UUID owner, Block block, Map<Block, BlockData> changed) {
        var lease = leases.get(block);
        if (lease == null || !lease.owners().containsKey(owner)) return;
        var previous = lease.placed();
        lease.owners().remove(owner);
        var next = lease.owners().isEmpty() ? lease.before() : lease.placed();
        if (!next.matches(previous) && block.getBlockData().matches(previous)) changed.put(block, next);
        if (lease.owners().isEmpty()) leases.remove(block);
    }

    private void remove(UUID id) {
        doors.remove(id);
        var entry = placed.remove(id);
        if (entry == null) return;
        var changed = new LinkedHashMap<Block, BlockData>();
        entry.blocks().forEach(block -> release(id, block, changed));
        BlockChanges.set(changed);
        entry.colliders().forEach(Shulker::remove);
        entry.anchors().forEach(ItemDisplay::remove);
        entry.entity().getPersistentDataContainer().remove(SNAPSHOT);
    }

    private void syncDoor(Placed entry) {
        var wanted = desired(entry.entity());
        if (!java.util.Set.copyOf(entry.blocks()).equals(wanted.keySet())) apply(entry, wanted);
    }

    @EventHandler public void added(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof ItemDisplay display) later(display);
    }

    @EventHandler public void removed(EntityRemoveEvent event) {
        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD) {
            doors.remove(event.getEntity().getUniqueId());
            var entry = placed.remove(event.getEntity().getUniqueId());
            if (entry != null) {
                entry.colliders().forEach(Shulker::remove);
                entry.anchors().forEach(ItemDisplay::remove);
            }
            return;
        }
        remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void place(NexoFurniturePlaceEvent event) {
        if (!available(desired(event.getBaseEntity()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void interact(NexoFurnitureInteractEvent event) {
        if (event.getMechanic().getDoor() == null || !FurnitureDoor.Companion.isOpen(event.getBaseEntity())) return;
        if (occupied(solidBlocks(event.getBaseEntity(), true).keySet())) event.setUseFurniture(Event.Result.DENY);
    }

    private static boolean occupied(Collection<Block> blocks) {
        return blocks.stream().anyMatch(block -> !block.getWorld().getNearbyEntities(BoundingBox.of(block), entity ->
                entity instanceof LivingEntity && !entity.getPersistentDataContainer().has(COLLIDER)
                        && !(entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR)).isEmpty());
    }

    public static boolean power(ItemDisplay entity, boolean enabled) {
        NexoFurniture.toggleLight(entity, enabled);
        Bukkit.getPluginManager().callEvent(new FurnitureChangeEvent(entity));
        return NexoFurniture.lightState(entity) == enabled;
    }

    public static Optional<ItemDisplay> place(String item, Location location, float yaw) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo") || !NexoFurniture.isFurniture(item)) return Optional.empty();
        return Optional.ofNullable(NexoFurniture.place(item, location, yaw, BlockFace.UP));
    }

    public static boolean remove(ItemDisplay entity) { return NexoFurniture.remove(entity); }
    public static void protect(ItemDisplay entity) { entity.getPersistentDataContainer().set(PROTECTED, PersistentDataType.BOOLEAN, true); }
    public static boolean lit(ItemDisplay entity) { return NexoFurniture.lightState(entity); }
    public static boolean open(ItemDisplay entity) { return FurnitureDoor.Companion.isOpen(entity); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void use(NexoFurnitureInteractEvent event) {
        var use = new FurnitureUseEvent(event.getPlayer(), event.getBaseEntity());
        Bukkit.getPluginManager().callEvent(use);
        if (use.isCancelled()) event.setUseFurniture(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void broken(NexoFurnitureBreakEvent event) {
        if (event.getBaseEntity().getPersistentDataContainer().has(PROTECTED)) event.setCancelled(true);
    }

    public static boolean door(ItemDisplay entity, boolean open) {
        var event = new FurnitureChangeEvent(entity, open);
        Bukkit.getPluginManager().callEvent(event);
        return event.applied();
    }

    @EventHandler public void changed(FurnitureChangeEvent event) {
        var entry = placed.get(event.entity().getUniqueId());
        if (entry == null) { later(event.entity()); return; }
        if (event.door().isPresent()) {
            var mechanic = NexoFurniture.furnitureMechanic(event.entity());
            if (mechanic == null || mechanic.getDoor() == null) return;
            boolean open = event.door().orElseThrow();
            if (!open && occupied(solidBlocks(event.entity(), true).keySet())) return;
            if (FurnitureDoor.Companion.isOpen(event.entity()) != open) mechanic.getDoor().toggleState(event.entity(), mechanic);
        }
        event.applied(apply(entry, desired(event.entity())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void lightInteraction(NexoFurnitureInteractEvent event) {
        if (event.getMechanic().getLight() != null && !event.getMechanic().getLight().isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> changed(new FurnitureChangeEvent(event.getBaseEntity())));
        }
    }

    @EventHandler public void teleport(EntityTeleportEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(COLLIDER)) event.setCancelled(true);
    }

    public static RayTraceResult rayTrace(Location start, Vector direction, double distance) {
        var block = start.getWorld().rayTraceBlocks(start, direction, distance, org.bukkit.FluidCollisionMode.NEVER, true);
        double limit = block == null ? distance : start.toVector().distance(block.getHitPosition());
        var box = BoundingBox.of(start, start.clone().add(direction.clone().multiply(limit))).expand(.01);
        RayTraceResult result = block;
        for (var entity : start.getWorld().getNearbyEntities(box, entity -> entity.getPersistentDataContainer().has(COLLIDER))) {
            var hit = entity.getBoundingBox().rayTrace(start.toVector(), direction, limit);
            if (hit != null) {
                limit = start.toVector().distance(hit.getHitPosition());
                result = new RayTraceResult(hit.getHitPosition(), start.getWorld().getBlockAt(hit.getHitPosition().toLocation(start.getWorld())), hit.getHitBlockFace());
            }
        }
        return result;
    }

    @Override public void close() {
        closed = true;
        task.cancel();
        HandlerList.unregisterAll(this);
        List.copyOf(placed.keySet()).forEach(this::remove);
    }
}
