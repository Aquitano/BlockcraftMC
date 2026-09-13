package me.blockcraft.lobby.npc;

import me.blockcraft.api.config.Locations;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.PetType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * The shop villager with its two floating name lines, respawned periodically after clearing round leftovers from
 * the spawn world; pets survive the purge. The NPC's own entities are not persisted, so a restart cannot leave
 * duplicates behind.
 */
public final class ShopNpc {

    private static final double TITLE_OFFSET = 1.3;
    private static final double SUBTITLE_OFFSET = 1.0;

    private final Plugin plugin;
    private final Locations<LobbyLocation> locations;
    private final Messages messages;
    private final List<Entity> spawned = new ArrayList<>();
    private BukkitTask respawnTask;

    public ShopNpc(Plugin plugin, Locations<LobbyLocation> locations, Messages messages) {
        this.plugin = plugin;
        this.locations = locations;
        this.messages = messages;
    }

    public void start(long respawnSeconds) {
        long ticks = respawnSeconds * 20L;
        respawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::respawn, 0L, ticks);
    }

    public void stop() {
        if (respawnTask != null) {
            respawnTask.cancel();
        }
        purge();
    }

    public void respawn() {
        purge();
        locations.get(LobbyLocation.SHOP_NPC).ifPresent(location -> {
            location.getChunk().load();
            spawned.add(location.getWorld().spawn(location, Villager.class, villager -> {
                villager.setAI(false);
                villager.setInvulnerable(true);
                villager.setSilent(true);
                villager.setPersistent(false);
            }));
            spawned.add(ownLine(location.clone().add(0, TITLE_OFFSET, 0), messages.get("npc.title")));
            spawned.add(ownLine(location.clone().add(0, SUBTITLE_OFFSET, 0), messages.get("npc.subtitle")));
        });
    }

    private static ArmorStand ownLine(Location location, Component text) {
        ArmorStand line = hologramLine(location, text);
        line.setPersistent(false);
        return line;
    }

    private void purge() {
        spawned.forEach(Entity::remove);
        spawned.clear();
        locations.get(LobbyLocation.SPAWN).ifPresent(spawn ->
                Worlds.removeLeftovers(spawn.getWorld(), entity -> PetType.of(entity).isPresent()));
    }

    public static ArmorStand hologramLine(Location location, Component text) {
        return location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.customName(text);
            stand.setCustomNameVisible(true);
        });
    }
}
