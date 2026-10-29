package me.blockcraft.lobby.activity;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.ChunkTickets;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/** The prisoner in the castle's dungeon, at the map's {@code prisoner} spot: he complains to whoever visits him. */
public final class Prisoner implements Listener, AutoCloseable {

    private final Locations<String> locations;
    private final Messages messages;
    private Mannequin prisoner;
    private final ChunkTickets.Owner held;

    public Prisoner(ChunkTickets chunks, Locations<String> locations, Messages messages) {
        held = chunks.owner();
        this.locations = locations;
        this.messages = messages;
        refresh();
    }

    public void refresh() {
        close();
        locations.get("prisoner").ifPresent(cell -> {
            held.add(cell.getChunk());
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
        held.clear();
    }
}
