package me.blockcraft.lobby;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Entities the lobby stands at its named spots, in groups, one per feature: a group keeps its spots' chunks loaded,
 * since entities that are never saved vanish with their chunk, turns a click or hit on any of its entities into its
 * reactions and removes them all on {@link Group#close}. Riders are removed with what they ride.
 */
public final class SpotEntities implements Listener {

    private final ChunkTickets chunks;
    private final Map<Entity, Group<?>> groups = new HashMap<>();

    public SpotEntities(ChunkTickets chunks) {
        this.chunks = chunks;
    }

    /** A group whose entities only stand there: clicks and hits on them are refused. */
    public <K> Group<K> group() {
        return group((player, key) -> { }, (player, key) -> { });
    }

    /** A group whose entities answer a main-hand click and a hit alike. */
    public <K> Group<K> group(BiConsumer<Player, K> touch) {
        return group(touch, touch);
    }

    public <K> Group<K> group(BiConsumer<Player, K> click, BiConsumer<Player, K> hit) {
        return new Group<>(click, hit);
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent event) {
        var group = groups.get(event.getRightClicked());
        if (group != null) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                group.click(event.getPlayer(), event.getRightClicked());
            }
        }
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        var group = groups.get(event.getAttacked());
        if (group != null) {
            event.setCancelled(true);
            group.hit(event.getPlayer(), event.getAttacked());
        }
    }

    public final class Group<K> implements AutoCloseable {

        private final BiConsumer<Player, K> click;
        private final BiConsumer<Player, K> hit;
        private final ChunkTickets.Owner held = chunks.owner();
        private final Map<Entity, K> keys = new HashMap<>();

        private Group(BiConsumer<Player, K> click, BiConsumer<Player, K> hit) {
            this.click = click;
            this.hit = hit;
        }

        /** Holds {@code spot}'s chunk until the group closes; each of {@code parts} stands for {@code key}. */
        public void add(K key, Location spot, Entity... parts) {
            held.add(spot.getChunk());
            for (var part : parts) {
                keys.put(part, key);
                groups.put(part, this);
            }
        }

        /** Whether something still stands for {@code key}; a command such as /kill can remove it behind the group's back. */
        public boolean stands(K key) {
            return keys.entrySet().stream().anyMatch(entry -> entry.getValue().equals(key) && entry.getKey().isValid());
        }

        /** Removes what stands for {@code key}; its chunk stays held. */
        public void remove(K key) {
            var parts = new ArrayList<Entity>();
            keys.forEach((part, owner) -> {
                if (owner.equals(key)) {
                    parts.add(part);
                }
            });
            remove(parts);
        }

        private void remove(List<Entity> parts) {
            for (var part : parts) {
                keys.remove(part);
                groups.remove(part);
                part.getPassengers().forEach(Entity::remove);
                part.remove();
            }
        }

        private void click(Player player, Entity entity) {
            click.accept(player, keys.get(entity));
        }

        private void hit(Player player, Entity entity) {
            hit.accept(player, keys.get(entity));
        }

        @Override
        public void close() {
            remove(List.copyOf(keys.keySet()));
            held.clear();
        }
    }
}
