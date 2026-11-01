package me.blockcraft.api.scenery;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.mechanics.furniture.FurnitureFactory;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent;
import me.blockcraft.api.scenery.FurnitureLayout.Placement;
import me.blockcraft.api.world.MapSaveEvent;
import me.blockcraft.common.calendar.NetworkCalendar;
import org.bukkit.Chunk;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class NexoScenery implements Listener, AutoCloseable {

    private record Placed(String item, ItemDisplay entity, Display fallback) { }

    private final Plugin plugin;
    private final World world;
    private final NetworkCalendar calendar;
    private final String group;
    private final FurnitureLayout layout;
    private final NamespacedKey owner;
    private final Map<String, Placed> placed = new HashMap<>();
    private final Set<Chunk> chunks = new HashSet<>();
    private final Set<String> warned = new HashSet<>();
    private final BukkitTask task;
    private boolean closed;
    private boolean saving;

    public NexoScenery(Plugin plugin, World world, NetworkCalendar calendar, String group, Path file) {
        this.plugin = plugin;
        this.world = world;
        this.calendar = calendar;
        this.group = group;
        layout = FurnitureLayout.load(file);
        owner = new NamespacedKey(plugin, "scenery");
        for (var placement : layout.placements()) {
            if (!placement.groups().isEmpty() && !placement.groups().contains(group)) {
                continue;
            }
            var chunk = location(placement).getChunk();
            if (chunks.add(chunk)) {
                chunk.addPluginChunkTicket(plugin);
                for (var entity : chunk.getEntities()) {
                    if (entity instanceof ItemDisplay && entity.getPersistentDataContainer().has(owner)) {
                        if (!NexoFurniture.remove(entity)) {
                            throw new IllegalStateException("Could not clean up furniture in " + world.getName());
                        }
                    }
                }
            }
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 1, 100);
    }

    private Location location(Placement placement) {
        return new Location(world, placement.x(), placement.y(), placement.z(), placement.yaw(), 0);
    }

    private void refresh() {
        if (closed || saving) {
            return;
        }
        var active = calendar.occasions();
        boolean added = false;
        for (var placement : layout.placements()) {
            var desired = placement.item(active, group);
            var current = placed.get(placement.id());
            if (current != null && current.entity().isValid() && desired.filter(current.item()::equals).isPresent()) {
                continue;
            }
            remove(placement.id());
            if (desired.isEmpty() || !NexoFurniture.isFurniture(desired.orElseThrow())) {
                continue;
            }
            String item = desired.orElseThrow();
            var at = location(placement);
            var mechanic = NexoFurniture.furnitureMechanic(item);
            if (mechanic == null || mechanic.notEnoughSpace(at, placement.yaw())) {
                warn(placement, "occupied footprint");
                continue;
            }
            var entity = NexoFurniture.place(item, at, placement.yaw(), BlockFace.UP);
            if (entity == null) {
                warn(placement, "Nexo refused the placement");
                continue;
            }
            entity.getPersistentDataContainer().set(owner, PersistentDataType.STRING, placement.id());
            placed.put(placement.id(), new Placed(item, entity, fallback(placement, item)));
            added = true;
        }
        if (added) {
            world.getPlayers().forEach(this::later);
        }
    }

    private Display fallback(Placement placement, String item) {
        var at = location(placement).getBlock().getLocation();
        Display display;
        if (placement.fallbackBlock().isPresent()) {
            display = world.spawn(at, BlockDisplay.class, block -> {
                block.setBlock(Bukkit.createBlockData(placement.fallbackBlock().orElseThrow()));
                block.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(1, placement.fallbackHeight(), 1), new Quaternionf()));
                block.setVisibleByDefault(false);
                block.setPersistent(false);
            });
        } else {
            display = world.spawn(at.add(0.5, 0.5, 0.5), ItemDisplay.class, fallback -> {
                fallback.setItemStack(new ItemStack(NexoItems.itemFromId(item).build().getType()));
                fallback.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                fallback.setRotation(placement.yaw(), 0);
                fallback.setVisibleByDefault(false);
                fallback.setPersistent(false);
            });
        }
        return display;
    }

    private void show(Player player) {
        if (closed || !player.getWorld().equals(world)) {
            return;
        }
        boolean pack = BlockcraftApi.get().resourcePack().applied(player);
        for (var prop : placed.values()) {
            if (pack) {
                player.hideEntity(plugin, prop.fallback());
            } else {
                player.showEntity(plugin, prop.fallback());
            }
            var mechanic = NexoFurniture.furnitureMechanic(prop.item());
            if (prop.entity().getTrackedBy().contains(player) && mechanic != null
                    && mechanic.getFactory() instanceof FurnitureFactory factory) {
                var packets = factory.packetManager();
                if (pack) {
                    packets.showFurniture(prop.entity().getEntityId(), prop.entity().getViewRange(), player);
                } else {
                    packets.hideFurniture(prop.entity().getEntityId(), player);
                }
            }
        }
    }

    private void later(Player player) {
        // Nexo sends its display metadata four ticks after tracking begins.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> show(player), 6);
    }

    @EventHandler
    public void onTrack(PlayerTrackEntityEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(owner)) {
            later(event.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        later(event.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        later(event.getPlayer());
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent event) {
        later(event.getPlayer());
    }

    private void warn(Placement placement, String reason) {
        if (warned.add(placement.id())) {
            plugin.getLogger().warning("Furniture " + placement.id() + " in " + world.getName() + ": " + reason);
        }
    }

    private void remove(String id) {
        var previous = placed.get(id);
        if (previous == null) {
            return;
        }
        if (previous.entity().isValid() && !NexoFurniture.remove(previous.entity())) {
            throw new IllegalStateException("Could not remove furniture " + id);
        }
        previous.fallback().remove();
        placed.remove(id);
    }

    private void clear() {
        List.copyOf(placed.keySet()).forEach(this::remove);
    }

    @EventHandler
    public void onItemsLoaded(NexoItemsLoadedEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!closed) {
                clear();
                warned.clear();
                refresh();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(NexoFurnitureBreakEvent event) {
        if (event.getBaseEntity().getPersistentDataContainer().has(owner)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSave(MapSaveEvent event) {
        saving = true;
        event.afterSave(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            saving = false;
            refresh();
        }));
        try {
            clear();
        } catch (RuntimeException failure) {
            event.abort(failure);
        }
    }

    @Override
    public void close() {
        closed = true;
        task.cancel();
        HandlerList.unregisterAll(this);
        clear();
        chunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        chunks.clear();
    }
}
