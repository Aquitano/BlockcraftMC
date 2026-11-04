package me.blockcraft.lobby.listener;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.activity.Parkour;
import me.blockcraft.lobby.config.LobbyConfig;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.Trails;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Pressure plates (premium area and fireworks) and particle trails. */
public final class MoveListener implements Listener {

    private static final List<Color> FIREWORK_COLORS = List.of(
            Color.AQUA, Color.BLACK, Color.BLUE, Color.FUCHSIA, Color.GRAY, Color.GREEN, Color.LIME, Color.MAROON,
            Color.NAVY, Color.OLIVE, Color.ORANGE, Color.PURPLE, Color.RED, Color.SILVER, Color.TEAL, Color.WHITE, Color.YELLOW);
    private static final List<FireworkEffect.Type> FIREWORK_TYPES = List.of(
            FireworkEffect.Type.BALL, FireworkEffect.Type.BALL_LARGE, FireworkEffect.Type.BURST, FireworkEffect.Type.CREEPER);

    private final Messages messages;
    private final LobbyConfig config;
    private final Locations<LobbyLocation> locations;
    private final Trails trails;
    private final Map<UUID, Long> fireworksShown = new HashMap<>();
    private final Duration fireworkCooldown;
    private final Parkour parkour;

    public MoveListener(Messages messages, LobbyConfig config, Locations<LobbyLocation> locations, Trails trails, Duration fireworkCooldown, Parkour parkour) {
        this.messages = messages;
        this.config = config;
        this.locations = locations;
        this.trails = trails;
        this.fireworkCooldown = fireworkCooldown;
        this.parkour = parkour;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Player player = event.getPlayer();
        player.setFallDistance(0.0f);
        trails.show(player);
        if (!event.hasChangedBlock()) {
            return;
        }
        if (parkour.running(player) || parkour.startsAt(event.getTo())) {
            return;
        }
        Material standingOn = event.getTo().getBlock().getType();
        if (standingOn == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            enterPremiumArea(player);
        } else if (standingOn == Material.STONE_PRESSURE_PLATE && ready(player)) {
            config.fireworkSpots().forEach(spot -> launchFirework(spot.toLocation(player.getWorld())));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fireworksShown.remove(event.getPlayer().getUniqueId());
    }

    private void enterPremiumArea(Player player) {
        if (player.hasPermission(LobbyPermissions.PREMIUM)) {
            locations.get(LobbyLocation.PREMIUM).ifPresent(player::teleport);
            player.sendMessage(messages.get("premium.entered"));
        } else {
            locations.get(LobbyLocation.SPAWN).ifPresent(player::teleport);
            messages.getLines("premium.required").forEach(player::sendMessage);
        }
    }

    private boolean ready(Player player) {
        long now = System.nanoTime();
        if (now - fireworksShown.getOrDefault(player.getUniqueId(), now - fireworkCooldown.toNanos()) < fireworkCooldown.toNanos()) {
            BlockcraftApi.get().notifications().feedback(player, messages.get("activities.firework-wait"));
            return false;
        }
        fireworksShown.put(player.getUniqueId(), now);
        return true;
    }

    public static void launchFirework(Location location) {
        var random = ThreadLocalRandom.current();
        World world = location.getWorld();
        world.spawn(location, Firework.class, firework -> {
            var meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .flicker(random.nextBoolean())
                    .trail(random.nextBoolean())
                    .withColor(FIREWORK_COLORS.get(random.nextInt(FIREWORK_COLORS.size())))
                    .withFade(FIREWORK_COLORS.get(random.nextInt(FIREWORK_COLORS.size())))
                    .with(FIREWORK_TYPES.get(random.nextInt(FIREWORK_TYPES.size())))
                    .build());
            meta.setPower(random.nextInt(1, 3));
            firework.setFireworkMeta(meta);
        });
    }
}
