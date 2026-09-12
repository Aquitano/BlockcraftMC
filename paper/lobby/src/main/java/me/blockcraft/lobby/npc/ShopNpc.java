package me.blockcraft.lobby.npc;

import me.blockcraft.api.config.Locations;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.LobbyLocation;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;

/** The shop villager with its two floating name lines; respawned periodically after clearing stray entities. */
public final class ShopNpc {

    private static final Set<EntityType> KEPT_ON_PURGE = Set.of(EntityType.PLAYER, EntityType.ITEM_FRAME, EntityType.MINECART);
    private static final double TITLE_OFFSET = 1.3;
    private static final double SUBTITLE_OFFSET = 1.0;

    private final Plugin plugin;
    private final Locations<LobbyLocation> locations;
    private final Messages messages;
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
            location.getWorld().spawn(location, Villager.class, villager -> {
                villager.setAI(false);
                villager.setInvulnerable(true);
                villager.setSilent(true);
            });
            hologramLine(location.clone().add(0, TITLE_OFFSET, 0), messages.get("npc.title"));
            hologramLine(location.clone().add(0, SUBTITLE_OFFSET, 0), messages.get("npc.subtitle"));
        });
    }

    private void purge() {
        locations.get(LobbyLocation.SPAWN).ifPresent(spawn -> {
            for (Entity entity : spawn.getWorld().getEntities()) {
                if (!KEPT_ON_PURGE.contains(entity.getType())) {
                    entity.remove();
                }
            }
        });
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
