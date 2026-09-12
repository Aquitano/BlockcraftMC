package me.blockcraft.lobby.cosmetic;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Trails {

    private static final int COUNT = 1;
    private static final double OFFSET = 1.0;
    private static final double SPEED = 0.05;

    private final Map<UUID, Trail> selected = new HashMap<>();

    public void select(Player player, Trail trail) {
        selected.put(player.getUniqueId(), trail);
    }

    public void clear(Player player) {
        selected.remove(player.getUniqueId());
    }

    public Optional<Trail> of(Player player) {
        return Optional.ofNullable(selected.get(player.getUniqueId()));
    }

    public void show(Player player) {
        of(player).ifPresent(trail ->
                player.spawnParticle(trail.particle(), player.getLocation(), COUNT, OFFSET, OFFSET, OFFSET, SPEED));
    }
}
