package me.blockcraft.lobby.cosmetic;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Leashed baby animals that follow their owner; one per player. */
public final class Pets {

    private final Messages messages;
    private final Map<UUID, UUID> petByOwner = new HashMap<>();

    public Pets(Messages messages) {
        this.messages = messages;
    }

    public Optional<PetType> of(Player owner) {
        return Optional.ofNullable(petByOwner.get(owner.getUniqueId()))
                .map(Bukkit::getEntity)
                .filter(Entity::isValid)
                .flatMap(PetType::of);
    }

    public void spawn(Player owner, PetType type) {
        Animals pet = owner.getWorld().spawn(owner.getLocation(), type.entityClass(), animal -> {
            animal.customName(messages.get("pets.entity-name", Placeholder.unparsed("player", owner.getName())));
            animal.setCustomNameVisible(true);
            animal.setRemoveWhenFarAway(true);
            if (type.baby()) {
                animal.setBaby();
            }
        });
        pet.setLeashHolder(owner);
        petByOwner.put(owner.getUniqueId(), pet.getUniqueId());
    }

    public boolean isOwner(Player player, Entity pet) {
        return pet.getUniqueId().equals(petByOwner.get(player.getUniqueId()));
    }

    public Optional<UUID> ownerOf(Entity pet) {
        return petByOwner.entrySet().stream()
                .filter(entry -> entry.getValue().equals(pet.getUniqueId()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public void release(UUID owner) {
        UUID petId = petByOwner.remove(owner);
        if (petId != null) {
            Entity pet = Bukkit.getEntity(petId);
            if (pet != null) {
                pet.remove();
            }
        }
    }
}
