package me.blockcraft.lobby.npc;

import me.blockcraft.api.config.Locations;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.PetType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The shop villager with its floating name card, respawned periodically after clearing round leftovers from
 * the spawn world; pets survive the purge. The NPC's own entities are not persisted, so a restart cannot leave
 * duplicates behind.
 */
public final class ShopNpc {

    private static final double NAME_OFFSET = 2.1;

    private final Plugin plugin;
    private final Locations<LobbyLocation> locations;
    private final Messages messages;
    private final Predicate<Entity> keep;
    private final List<Entity> spawned = new ArrayList<>();
    private BukkitTask respawnTask;

    public ShopNpc(Plugin plugin, Locations<LobbyLocation> locations, Messages messages, Predicate<Entity> keep) {
        this.plugin = plugin;
        this.locations = locations;
        this.messages = messages;
        this.keep = keep;
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
            spawned.add(ownLine(location.clone().add(0, NAME_OFFSET, 0),
                    Component.join(JoinConfiguration.newlines(), messages.get("npc.title"), messages.get("npc.subtitle"))));
        });
    }

    private static TextDisplay ownLine(Location location, Component text) {
        var line = Holograms.line(location, text);
        line.setPersistent(false);
        return line;
    }

    private void purge() {
        spawned.forEach(Entity::remove);
        spawned.clear();
        locations.get(LobbyLocation.SPAWN).ifPresent(spawn ->
                Worlds.removeLeftovers(spawn.getWorld(), entity -> PetType.of(entity).isPresent() || keep.test(entity)));
    }
}
