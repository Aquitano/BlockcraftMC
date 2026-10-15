package me.blockcraft.bedwars.lobby;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A figure in the lobby that every player sees in their own skin and only for themselves; above it, the left and
 * right clicks they landed on it in the last second.
 */
public final class CpsTester implements Listener {

    private static final double COUNTER_HEIGHT = 2.2;
    private static final long SECOND = 20L;

    private record Dummy(Mannequin figure, TextDisplay counter) {
    }

    private final Plugin plugin;
    private final Optional<Location> spot;
    private final Messages messages;
    private final Map<UUID, Dummy> dummies = new HashMap<>();
    private final Map<UUID, int[]> clicks = new HashMap<>();
    private BukkitTask task;

    public CpsTester(Plugin plugin, Optional<Location> spot, Messages messages) {
        this.plugin = plugin;
        this.spot = spot;
        this.messages = messages;
    }

    public void start() {
        spot.ifPresent(location -> {
            location.getChunk().addPluginChunkTicket(plugin);
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::count, SECOND, SECOND);
        });
    }

    public void show(Player viewer) {
        spot.ifPresent(location -> {
            hide(viewer);
            var figure = Figures.spawn(location, ResolvableProfile.resolvableProfile(viewer.getPlayerProfile()),
                    messages.get("lobby.cps.name"));
            var counter = location.getWorld().spawn(location.clone().add(0, COUNTER_HEIGHT, 0), TextDisplay.class, display -> {
                display.text(line(0, 0));
                display.setBillboard(Display.Billboard.CENTER);
                display.setPersistent(false);
            });
            figure.setVisibleByDefault(false);
            counter.setVisibleByDefault(false);
            viewer.showEntity(plugin, figure);
            viewer.showEntity(plugin, counter);
            dummies.put(viewer.getUniqueId(), new Dummy(figure, counter));
        });
    }

    public void hide(Player viewer) {
        var dummy = dummies.remove(viewer.getUniqueId());
        clicks.remove(viewer.getUniqueId());
        if (dummy != null) {
            dummy.figure().remove();
            dummy.counter().remove();
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        dummies.values().forEach(dummy -> {
            dummy.figure().remove();
            dummy.counter().remove();
        });
        dummies.clear();
        clicks.clear();
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        var player = event.getPlayer();
        var dummy = dummies.get(player.getUniqueId());
        if (dummy == null || !dummy.figure().equals(event.getAttacked())) {
            return;
        }
        event.setCancelled(true);
        clicks.computeIfAbsent(player.getUniqueId(), ignored -> new int[2])[0]++;
        dummy.figure().playHurtAnimation(0.0f);
        player.playSound(dummy.figure().getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
    }

    @EventHandler
    public void onUse(PlayerInteractEntityEvent event) {
        var player = event.getPlayer();
        var dummy = dummies.get(player.getUniqueId());
        if (dummy == null || event.getHand() != EquipmentSlot.HAND || !dummy.figure().equals(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        clicks.computeIfAbsent(player.getUniqueId(), ignored -> new int[2])[1]++;
    }

    private void count() {
        dummies.forEach((viewer, dummy) -> {
            var counted = clicks.getOrDefault(viewer, new int[2]);
            dummy.counter().text(line(counted[0], counted[1]));
        });
        clicks.clear();
    }

    private Component line(int left, int right) {
        return messages.get("lobby.cps.counter", Formatter.number("left", left), Formatter.number("right", right));
    }
}
