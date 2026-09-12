package me.blockcraft.lobby.listener;

import me.blockcraft.api.config.Locations;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Pressure plates (premium area, one-time fireworks show) and particle trails. */
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
    private final Set<UUID> fireworksShown = new HashSet<>();

    public MoveListener(Messages messages, LobbyConfig config, Locations<LobbyLocation> locations, Trails trails) {
        this.messages = messages;
        this.config = config;
        this.locations = locations;
        this.trails = trails;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        player.setFallDistance(-100.0f);
        trails.show(player);
        Material standingOn = player.getLocation().getBlock().getType();
        if (standingOn == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            enterPremiumArea(player);
        } else if (standingOn == Material.STONE_PRESSURE_PLATE && fireworksShown.add(player.getUniqueId())) {
            config.fireworkSpots().forEach(spot -> launchFirework(spot.toLocation(player.getWorld())));
        }
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

    private static void launchFirework(Location location) {
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
