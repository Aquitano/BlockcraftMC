package me.blockcraft.lobby.activity;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import org.bukkit.Chunk;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

/** The prisoner in the castle's dungeon, at the map's {@code prisoner} spot: he complains to whoever visits him. */
public final class Prisoner implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final Locations<String> locations;
    private final Messages messages;
    private Mannequin prisoner;
    private Chunk held;

    public Prisoner(Plugin plugin, Locations<String> locations, Messages messages) {
        this.plugin = plugin;
        this.locations = locations;
        this.messages = messages;
        refresh();
    }

    public void refresh() {
        close();
        locations.get("prisoner").ifPresent(cell -> {
            held = cell.getChunk();
            held.addPluginChunkTicket(plugin);
            prisoner = Figures.spawn(cell, Skins.PRISONER, messages.get("prisoner.name"));
        });
    }

    @EventHandler
    public void onTalk(PlayerInteractEntityEvent event) {
        if (prisoner == null || !event.getRightClicked().equals(prisoner)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            Sounds.CHAINS.play(event.getPlayer());
            event.getPlayer().sendMessage(messages.pick("prisoner.talk"));
        }
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        if (prisoner != null && event.getAttacked().equals(prisoner)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.pick("prisoner.hurt"));
        }
    }

    @Override
    public void close() {
        if (prisoner != null) {
            prisoner.remove();
            prisoner = null;
        }
        if (held != null) {
            held.removePluginChunkTicket(plugin);
            held = null;
        }
    }
}
