package me.blockcraft.lobby.cosmetic;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.settings.PlayerSetting;
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
    private final Map<UUID, Integer> lastEmission = new HashMap<>();
    private final int intervalTicks;
    private final int viewDistance;

    public Trails(int intervalTicks, int viewDistance) {
        this.intervalTicks = intervalTicks;
        this.viewDistance = viewDistance;
    }

    public void select(Player player, Trail trail) {
        selected.put(player.getUniqueId(), trail);
    }

    public void clear(Player player) {
        selected.remove(player.getUniqueId());
        lastEmission.remove(player.getUniqueId());
    }

    public Optional<Trail> of(Player player) {
        return Optional.ofNullable(selected.get(player.getUniqueId()));
    }

    public void show(Player player) {
        of(player).ifPresent(trail -> {
            int now = player.getTicksLived();
            var last = lastEmission.get(player.getUniqueId());
            if (last != null && now - last < intervalTicks) {
                return;
            }
            lastEmission.put(player.getUniqueId(), now);
            var location = player.getLocation();
            for (var viewer : player.getWorld().getNearbyPlayers(location, viewDistance)) {
                if (viewer.canSee(player)
                        && BlockcraftApi.get().settings().enabled(viewer, PlayerSetting.EFFECTS)) {
                    viewer.spawnParticle(trail.particle(), location, COUNT, OFFSET, OFFSET, OFFSET, SPEED);
                }
            }
        });
    }
}
