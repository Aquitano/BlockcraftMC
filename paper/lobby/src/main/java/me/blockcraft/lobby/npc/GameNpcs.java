package me.blockcraft.lobby.npc;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.network.ServerGroups;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.activity.Figures;
import me.blockcraft.lobby.config.GameNpcConfig;
import me.blockcraft.lobby.config.LobbyLocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * A figure in front of every game's area, at the map's {@code npc-<area>} spot: it holds the game's item, a card
 * above it names the game, says whether it is open and how many play, and a click of either hand joins the game the
 * way the navigator does. It turns to the nearest player within {@code look-radius} and now and then swings its arm
 * or crouches. Without NMS a figure's head turns for every viewer alike, so it faces the nearest player.
 */
public final class GameNpcs implements Listener, AutoCloseable {

    private static final long LOOK_TICKS = 5L;
    private static final double EYES = 1.62;
    private static final double CARD_HEIGHT = 2.6;
    private static final long CROUCH_TICKS = 12L;
    private static final long CLICK_GAP_MILLIS = 1000L;

    private record Npc(LobbyLocation area, Location base, Mannequin figure, TextDisplay card) {
    }

    private final Plugin plugin;
    private final Locations<String> spots;
    private final GameNpcConfig config;
    private final Map<LobbyLocation, String> groups;
    private final ServerGroups status;
    private final Messages messages;
    private final Messages shared;
    private final List<Npc> npcs = new ArrayList<>();
    private final List<Chunk> held = new ArrayList<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Random random = new Random();
    private final BukkitTask look;
    private final BukkitTask idle;

    public GameNpcs(Plugin plugin, Locations<String> spots, GameNpcConfig config, Map<LobbyLocation, String> groups,
                    ServerGroups status, Messages messages, Messages shared) {
        this.plugin = plugin;
        this.spots = spots;
        this.config = config;
        this.groups = groups;
        this.status = status;
        this.messages = messages;
        this.shared = shared;
        refresh();
        status.onRefresh(plugin, this::relabel);
        look = Bukkit.getScheduler().runTaskTimer(plugin, this::look, LOOK_TICKS, LOOK_TICKS);
        long idleTicks = config.idleSeconds() * 20L;
        idle = Bukkit.getScheduler().runTaskTimer(plugin, this::idle, idleTicks, idleTicks);
    }

    public static String spot(LobbyLocation area) {
        return "npc-" + area.key();
    }

    /** The {@code /lobbyset} keys of the figures, one per game area. */
    public static List<String> spots() {
        return Arrays.stream(LobbyLocation.values()).filter(area -> area.game().isPresent()).map(GameNpcs::spot).toList();
    }

    /** Where the figures stand, by area, for whatever else marks a game's area. */
    public Map<LobbyLocation, Location> places() {
        var places = new HashMap<LobbyLocation, Location>();
        npcs.forEach(npc -> places.put(npc.area(), npc.base()));
        return places;
    }

    public void refresh() {
        removeAll();
        for (var area : LobbyLocation.values()) {
            area.game().ifPresent(icon -> spots.get(spot(area)).ifPresent(base -> {
                var chunk = base.getChunk();
                chunk.addPluginChunkTicket(plugin);
                held.add(chunk);
                var figure = Figures.spawn(base, skin(area), spawned -> spawned.getEquipment().setItemInMainHand(icon.item().build()));
                var card = Holograms.line(base.clone().add(0, CARD_HEIGHT, 0), card(area));
                card.setPersistent(false);
                npcs.add(new Npc(area, base.clone(), figure, card));
            }));
        }
    }

    private ResolvableProfile skin(LobbyLocation area) {
        return config.skin(area).map(name -> ResolvableProfile.resolvableProfile().name(name).build())
                .orElseGet(Mannequin::defaultProfile);
    }

    private Component card(LobbyLocation area) {
        var lines = new ArrayList<Component>();
        lines.add(messages.get("navigator." + area.key()));
        Optional.ofNullable(groups.get(area)).filter(group -> !group.isBlank()).map(status::status).ifPresentOrElse(now -> {
            lines.add(shared.get("game-status." + now.availability().name().toLowerCase(Locale.ROOT)));
            lines.add(messages.get("game-npcs.online", Formatter.number("online", now.online())));
            lines.add(messages.get(now.joinable() ? "game-npcs.play" : "game-npcs.retry"));
        }, () -> lines.add(messages.get("game-npcs.unavailable")));
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private void relabel() {
        npcs.forEach(npc -> npc.card().text(card(npc.area())));
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent event) {
        byFigure(event.getRightClicked()).ifPresent(npc -> {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                play(event.getPlayer(), npc);
            }
        });
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        byFigure(event.getAttacked()).ifPresent(npc -> {
            event.setCancelled(true);
            play(event.getPlayer(), npc);
        });
    }

    /** The proxy checks availability at the moment of the click, as it does for the navigator and the signs. */
    private void play(Player player, Npc npc) {
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < CLICK_GAP_MILLIS) {
            return;
        }
        var group = groups.get(npc.area());
        if (group == null || group.isBlank()) {
            player.sendMessage(messages.get("navigator.unavailable"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        Sounds.MENU_CLICK.play(player);
        npc.figure().swingMainHand();
        ProxyMessaging.play(plugin, player, group);
    }

    private Optional<Npc> byFigure(Entity entity) {
        return npcs.stream().filter(npc -> npc.figure().equals(entity)).findFirst();
    }

    private void look() {
        var api = BlockcraftApi.get();
        double radiusSquared = config.lookRadius() * config.lookRadius();
        for (var npc : npcs) {
            var eyes = npc.base().clone().add(0, EYES, 0);
            var nearest = eyes.getWorld().getPlayers().stream()
                    .filter(player -> player.getGameMode() != GameMode.SPECTATOR && !api.isVanished(player))
                    .filter(player -> player.getEyeLocation().distanceSquared(eyes) <= radiusSquared)
                    .min(Comparator.comparingDouble(player -> player.getEyeLocation().distanceSquared(eyes)));
            if (nearest.isPresent()) {
                var toward = nearest.get().getEyeLocation().toVector().subtract(eyes.toVector());
                float yaw = (float) Math.toDegrees(Math.atan2(-toward.getX(), toward.getZ()));
                float pitch = (float) -Math.toDegrees(Math.atan2(toward.getY(), Math.hypot(toward.getX(), toward.getZ())));
                npc.figure().setRotation(yaw, pitch);
            } else if (npc.figure().getYaw() != npc.base().getYaw() || npc.figure().getPitch() != 0) {
                npc.figure().setRotation(npc.base().getYaw(), 0);
            }
        }
    }

    /** One figure a turn swings its arm or, where its pose allows, crouches for a moment. */
    private void idle() {
        if (npcs.isEmpty()) {
            return;
        }
        var figure = npcs.get(random.nextInt(npcs.size())).figure();
        if (random.nextBoolean() || !Mannequin.validPoses().contains(Pose.SNEAKING)) {
            figure.swingMainHand();
            return;
        }
        figure.setPose(Pose.SNEAKING, true);
        Tasks.later(plugin, CROUCH_TICKS, () -> {
            if (figure.isValid()) {
                figure.setPose(Pose.STANDING, false);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    private void removeAll() {
        npcs.forEach(npc -> {
            npc.figure().remove();
            npc.card().remove();
        });
        npcs.clear();
        held.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        held.clear();
    }

    @Override
    public void close() {
        look.cancel();
        idle.cancel();
        removeAll();
    }
}
