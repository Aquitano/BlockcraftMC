package me.blockcraft.lobby.listener;

import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.cosmetic.PetType;
import me.blockcraft.lobby.cosmetic.Pets;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class PetListener implements Listener {

    private final Pets pets;
    private final Messages messages;

    public PetListener(Pets pets, Messages messages) {
        this.pets = pets;
        this.messages = messages;
    }

    @EventHandler
    public void onUnleash(EntityUnleashEvent event) {
        Entity entity = event.getEntity();
        entity.remove();
        pets.ownerOf(entity).ifPresent(pets::release);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (PetType.of(event.getEntity()).isPresent()) {
            event.getDrops().clear();
            pets.ownerOf(event.getEntity()).ifPresent(pets::release);
        }
    }

    @EventHandler
    public void onLeadDrop(ItemSpawnEvent event) {
        if (event.getEntity().getItemStack().getType() == Material.LEAD) {
            event.getEntity().remove();
        }
    }

    @EventHandler
    public void onPetClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof LivingEntity animal)) {
            return;
        }
        PetType.of(animal).ifPresent(type -> {
            Player player = event.getPlayer();
            if (!animal.isLeashed()) {
                animal.remove();
            } else if (pets.isOwner(player, animal)) {
                pets.release(player.getUniqueId());
                player.sendMessage(messages.get("pets.removed." + type.key()));
            }
        });
    }
}
