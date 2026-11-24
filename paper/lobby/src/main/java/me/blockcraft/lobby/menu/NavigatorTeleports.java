package me.blockcraft.lobby.menu;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class NavigatorTeleports {

    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public Optional<CompletableFuture<Boolean>> start(Player player, Location destination) {
        var id = player.getUniqueId();
        if (!pending.add(id)) {
            return Optional.empty();
        }
        try {
            var teleport = player.teleportAsync(destination);
            teleport.whenComplete((result, failure) -> pending.remove(id));
            return Optional.of(teleport);
        } catch (RuntimeException failure) {
            pending.remove(id);
            throw failure;
        }
    }
}
