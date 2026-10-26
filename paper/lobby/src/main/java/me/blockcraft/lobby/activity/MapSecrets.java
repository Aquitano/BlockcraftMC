package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.cosmetic.Secrets;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The secrets the map's builders hid without a sign, at spots of the map's activities.yml: places found by walking
 * in ({@code secret-<key>}), the cake next to "Gute Nacht!" ({@code cake}), and three ender eyes in their frames
 * ({@code eye-1} to {@code eye-3}) that, once taken, fill the empty frame in the hut ({@code eye-frame}) and carry
 * the player to the old beta server ({@code eye-arrival}).
 */
public final class MapSecrets implements Listener, AutoCloseable {

    public static final List<String> BLOCK_SPOTS = List.of("cake", "eye-1", "eye-2", "eye-3", "eye-frame");
    public static final List<String> SPOTS = spots();

    private static final long CHECK_TICKS = 10L;
    private static final long ARRIVAL_TICKS = 40L;
    private static final int EYES = 3;
    private static final String EYE_ITEM = "lobby.eye.";

    private final Plugin plugin;
    private final Locations<String> spots;
    private final Messages messages;
    private final Secrets secrets;
    private final double radiusSquared;
    private final Set<UUID> busy = new HashSet<>();
    private final BukkitTask check;

    public MapSecrets(Plugin plugin, Locations<String> spots, Messages messages, Secrets secrets, double radius) {
        this.plugin = plugin;
        this.spots = spots;
        this.messages = messages;
        this.secrets = secrets;
        this.radiusSquared = radius * radius;
        this.check = Bukkit.getScheduler().runTaskTimer(plugin, this::visit, CHECK_TICKS, CHECK_TICKS);
    }

    public static String spot(Secret secret) {
        return "secret-" + secret.key().toLowerCase(Locale.ROOT);
    }

    private static List<String> spots() {
        var places = Arrays.stream(Secret.values()).filter(secret -> secret.trigger() == Secret.Trigger.PLACE).map(MapSecrets::spot);
        return Stream.of(places, BLOCK_SPOTS.stream(), Stream.of("eye-arrival")).flatMap(spot -> spot).toList();
    }

    private void visit() {
        var api = BlockcraftApi.get();
        for (var player : Bukkit.getOnlinePlayers()) {
            if (api.isVanished(player)) {
                continue;
            }
            var at = player.getLocation();
            for (var secret : Secret.values()) {
                if (secret.trigger() == Secret.Trigger.PLACE && spots.get(spot(secret))
                        .filter(place -> place.getWorld().equals(at.getWorld()) && place.distanceSquared(at) <= radiusSquared).isPresent()) {
                    secrets.discover(player, secret);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || block == null) {
            return;
        }
        var player = event.getPlayer();
        if (block.getType() == Material.CAKE && at("cake", block)) {
            event.setCancelled(true);
            player.sendMessage(messages.pick("secrets.cake"));
            secrets.discover(player, Secret.KUCHEN);
            return;
        }
        if (block.getType() != Material.END_PORTAL_FRAME) {
            return;
        }
        if (at("eye-frame", block)) {
            event.setCancelled(true);
            fill(player, block);
            return;
        }
        IntStream.rangeClosed(1, EYES).filter(eye -> at("eye-" + eye, block)).findFirst().ifPresent(eye -> {
            event.setCancelled(true);
            take(player, eye);
        });
    }

    private boolean at(String spot, Block block) {
        return spots.get(spot).map(Location::getBlock).filter(block::equals).isPresent();
    }

    private void take(Player player, int eye) {
        if (!busy.add(player.getUniqueId())) {
            return;
        }
        var unlocks = BlockcraftApi.get().unlocks();
        Tasks.async(plugin, () -> unlocks.unlock(player.getUniqueId(), EYE_ITEM + eye)
                ? Optional.of(unlocks.owned(player.getUniqueId(), EYE_ITEM).size()) : Optional.<Integer>empty(), taken -> {
            busy.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            taken.ifPresentOrElse(count -> {
                Sounds.EYE.play(player);
                player.sendMessage(messages.get(count >= EYES ? "secrets.eye-last" : "secrets.eye-taken",
                        Formatter.number("found", count), Formatter.number("total", EYES)));
            }, () -> player.sendMessage(messages.get("secrets.eye-known")));
        }, () -> busy.remove(player.getUniqueId()));
    }

    /** With all three eyes the hut's frame lights up for the player, and a moment later they stand on the beta server. */
    private void fill(Player player, Block frame) {
        if (!busy.add(player.getUniqueId())) {
            return;
        }
        Tasks.async(plugin, () -> BlockcraftApi.get().unlocks().owned(player.getUniqueId(), EYE_ITEM).size(), count -> {
            busy.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (count < EYES) {
                player.sendMessage(messages.get("secrets.eye-missing", Formatter.number("found", count), Formatter.number("total", EYES)));
                return;
            }
            if (frame.getBlockData() instanceof EndPortalFrame data) {
                data.setEye(true);
                player.sendBlockChange(frame.getLocation(), data);
            }
            Sounds.EYE.play(player);
            player.sendMessage(messages.get("secrets.eye-filled"));
            secrets.discover(player, Secret.ENDERAUGE);
            spots.get("eye-arrival").ifPresent(arrival -> Tasks.later(plugin, ARRIVAL_TICKS, () -> {
                if (player.isOnline()) {
                    player.teleport(arrival);
                    Sounds.TELEPORT.play(player);
                }
            }));
        }, () -> busy.remove(player.getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        busy.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        check.cancel();
    }
}
