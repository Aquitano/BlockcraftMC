package me.blockcraft.api.scenery;

import me.blockcraft.api.world.MapSaveEvent;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Parrot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AmbientCreatures implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final World world;
    private final CreatureLayout layout;
    private final Map<String, Mob> creatures = new HashMap<>();
    private final Set<Chunk> chunks = new HashSet<>();
    private final BukkitTask task;
    private long ticks;
    private boolean saving;
    private boolean closed;

    public AmbientCreatures(Plugin plugin, World world, Path file) {
        this.plugin = plugin;
        this.world = world;
        layout = CreatureLayout.load(file);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::animate, 1, layout.stepTicks());
    }

    private void animate() {
        if (saving || closed || world.getPlayers().isEmpty()) {
            return;
        }
        for (var creature : layout.creatures()) {
            var at = creature.pose(ticks).in(world);
            if (chunks.add(at.getChunk())) {
                at.getChunk().addPluginChunkTicket(plugin);
            }
            var entity = creatures.get(creature.id());
            if (entity == null || !entity.isValid()) {
                entity = spawn(creature, at);
                creatures.put(creature.id(), entity);
            } else if (!entity.getLocation().equals(at)) {
                entity.teleport(at);
            }
            if (creature instanceof CreatureLayout.PerchedBird bird && entity instanceof Parrot parrot) {
                parrot.setBodyYaw(bird.at().yaw());
                parrot.setSitting(at.getY() <= bird.at().y());
            }
        }
        ticks += layout.stepTicks();
    }

    private Mob spawn(CreatureLayout.Creature creature, Location at) {
        return switch (creature) {
            case CreatureLayout.BeeFlight ignored -> world.spawn(at, Bee.class, bee -> {
                prepare(bee);
                bee.setAdult();
            });
            case CreatureLayout.PerchedBird ignored -> world.spawn(at, Parrot.class, bird -> {
                prepare(bird);
                bird.setVariant(Parrot.Variant.GRAY);
            });
        };
    }

    private static void prepare(Mob creature) {
        creature.setPersistent(false);
        creature.setRemoveWhenFarAway(false);
        creature.setAI(false);
        creature.setGravity(false);
        creature.setInvulnerable(true);
        creature.setSilent(true);
        creature.setCollidable(false);
        creature.setCanPickupItems(false);
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (creatures.containsValue(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        onInteract(event);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (creatures.containsValue(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSave(MapSaveEvent event) {
        if (event.world().equals(world)) {
            saving = true;
            event.suspend(this::clear, () -> saving = false);
        }
    }

    private void clear() {
        creatures.values().forEach(Entity::remove);
        creatures.clear();
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
