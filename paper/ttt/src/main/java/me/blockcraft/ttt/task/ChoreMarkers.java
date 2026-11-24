package me.blockcraft.ttt.task;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Private markers remain visible through walls. The selected objective has a label; secondary markers use smaller
 * symbols. Both scale with distance, and nearby stations have particles.
 */
final class ChoreMarkers {

    record Marker(Component text, boolean focused) {
    }

    private static final double LIFT = 1.4;
    private static final float NEAR_SCALE = 2.5f;
    private static final float FAR_SCALE = 24f;
    private static final float SCALE_PER_BLOCK = 0.3f;
    private static final float RESCALE_FROM = 0.1f;
    private static final int RESCALE_TICKS = 20;
    private static final float VIEW_RANGE = 4f;
    private static final double SPARKS_WITHIN = 12.0;
    private static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);

    private final Plugin plugin;
    private final Map<UUID, Map<Block, TextDisplay>> shown = new HashMap<>();

    ChoreMarkers(Plugin plugin) {
        this.plugin = plugin;
    }

    void show(Player player, Map<Block, Marker> spots) {
        var mine = shown.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>());
        mine.entrySet().removeIf(entry -> {
            if (spots.containsKey(entry.getKey()) && entry.getValue().isValid()) {
                return false;
            }
            entry.getValue().remove();
            return true;
        });
        spots.forEach((spot, mark) -> {
            var display = mine.get(spot);
            if (display == null) {
                mine.put(spot, spawn(player, spot, mark));
                return;
            }
            if (!display.text().equals(mark.text())) {
                display.text(mark.text());
            }
            resize(player, display, mark.focused());
        });
    }

    void hide(UUID player) {
        var mine = shown.remove(player);
        if (mine != null) {
            mine.values().forEach(TextDisplay::remove);
        }
    }

    void keepOnly(Set<UUID> players) {
        Set.copyOf(shown.keySet()).stream().filter(id -> !players.contains(id)).forEach(this::hide);
    }

    void sparkle() {
        shown.forEach((id, mine) -> {
            var player = Bukkit.getPlayer(id);
            if (player == null) {
                return;
            }
            mine.keySet().forEach(spot -> {
                var center = spot.getLocation().add(0.5, 0.5, 0.5);
                if (center.getWorld().equals(player.getWorld())
                        && center.distanceSquared(player.getLocation()) <= SPARKS_WITHIN * SPARKS_WITHIN) {
                    player.spawnParticle(Particle.WAX_ON, center, 6, 0.4, 0.4, 0.4, 0);
                }
            });
        });
    }

    void clear() {
        shown.values().forEach(mine -> mine.values().forEach(TextDisplay::remove));
        shown.clear();
    }

    private static void resize(Player viewer, TextDisplay display, boolean focused) {
        float wanted = scale(viewer, display.getLocation(), focused);
        float now = display.getTransformation().getScale().x();
        if (Math.abs(wanted - now) > now * RESCALE_FROM) {
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(RESCALE_TICKS);
            display.setTransformation(scaled(wanted));
        }
    }

    private static float scale(Player viewer, Location at, boolean focused) {
        double distance = at.getWorld().equals(viewer.getWorld()) ? at.distance(viewer.getEyeLocation()) : 0;
        return Math.clamp((float) distance * SCALE_PER_BLOCK, NEAR_SCALE, FAR_SCALE) * (focused ? 0.25f : 0.4f);
    }

    private static Transformation scaled(float scale) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale), new AxisAngle4f());
    }

    private TextDisplay spawn(Player viewer, Block spot, Marker mark) {
        Location at = spot.getLocation().add(0.5, LIFT, 0.5);
        var display = spot.getWorld().spawn(at, TextDisplay.class, entity -> {
            entity.setVisibleByDefault(false);
            entity.setPersistent(false);
            entity.text(mark.text());
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            entity.setShadowed(true);
            entity.setBrightness(FULL_BRIGHT);
            entity.setViewRange(VIEW_RANGE);
            entity.setTransformation(scaled(scale(viewer, at, mark.focused())));
        });
        viewer.showEntity(plugin, display);
        return display;
    }
}
