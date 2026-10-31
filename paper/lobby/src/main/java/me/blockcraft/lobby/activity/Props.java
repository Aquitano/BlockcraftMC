package me.blockcraft.lobby.activity;

import me.blockcraft.api.config.Locations;
import me.blockcraft.api.item.Items;
import me.blockcraft.lobby.config.PropsConfig;
import me.blockcraft.lobby.config.PropsConfig.BlockLook;
import me.blockcraft.lobby.config.PropsConfig.ItemLook;
import me.blockcraft.lobby.config.PropsConfig.Part;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The lobby's set pieces: each configured prop with a spot is a group of block and item displays round that spot,
 * which turns and bobs as one. The displays are not saved with the map; they are spawned at every start from the
 * spots, so changing a prop in config.yml changes it everywhere it stands. They move by keyframes: every few ticks
 * each part gets its next transformation and the client interpolates towards it, which costs the server one update
 * per part and keyframe.
 */
public final class Props {

    private static final float FULL_TURN = (float) (Math.PI * 2);
    private static final float BLOCK_CENTRE = 0.5f;

    private record Placed(Display display, Part part) {
    }

    private final Plugin plugin;
    private final PropsConfig config;
    private final Locations<String> spots;
    private final Map<String, List<Placed>> placed = new HashMap<>();
    private BukkitTask task;
    private long ticks;

    public Props(Plugin plugin, PropsConfig config, Locations<String> spots) {
        this.plugin = plugin;
        this.config = config;
        this.spots = spots;
    }

    public Set<String> names() {
        return config.props().keySet();
    }

    public void start() {
        config.props().keySet().forEach(name -> spots.get(name).ifPresent(spot -> spawn(name, spot)));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::animate, config.keyframeTicks(), config.keyframeTicks());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        placed.keySet().stream().toList().forEach(this::despawn);
    }

    /** Puts the prop {@code name} at {@code spot}, where it stays after restarts. */
    public void place(String name, Location spot) {
        var centred = spot.getBlock().getLocation().add(BLOCK_CENTRE, 0, BLOCK_CENTRE);
        spots.set(name, centred);
        spawn(name, centred);
    }

    private void spawn(String name, Location spot) {
        despawn(name);
        var prop = config.props().get(name);
        var displays = new ArrayList<Placed>();
        for (var part : prop.parts()) {
            Display display = switch (part.look()) {
                case BlockLook block -> spot.getWorld().spawn(spot, BlockDisplay.class,
                        spawned -> spawned.setBlock(block.material().createBlockData()));
                case ItemLook item -> spot.getWorld().spawn(spot, ItemDisplay.class, spawned -> {
                    var stack = Items.of(item.material());
                    item.model().ifPresent(model -> model.mark(stack));
                    if (item.glint()) {
                        stack.glow();
                    }
                    spawned.setItemStack(stack.build());
                    spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                });
            };
            display.setPersistent(false);
            display.setViewRange(config.viewRange());
            display.setTransformation(pose(part, 0, 0));
            displays.add(new Placed(display, part));
        }
        placed.put(name, displays);
    }

    private void despawn(String name) {
        var displays = placed.remove(name);
        if (displays != null) {
            displays.forEach(each -> each.display().remove());
        }
    }

    private void animate() {
        ticks += config.keyframeTicks();
        placed.forEach((name, displays) -> {
            var prop = config.props().get(name);
            float spin = prop.spinSeconds() > 0 ? (float) (FULL_TURN * ticks / (prop.spinSeconds() * 20)) : 0;
            float lift = (float) (prop.bob() * Math.sin(FULL_TURN * ticks / (prop.bobSeconds() * 20)));
            for (var each : displays) {
                if (each.part().still() || !each.display().isValid()) {
                    continue;
                }
                each.display().setInterpolationDelay(0);
                each.display().setInterpolationDuration(config.keyframeTicks());
                each.display().setTransformation(pose(each.part(), spin, lift));
            }
        });
    }

    /**
     * Where a part stands with the whole prop turned by {@code spin} radians round its spot and lifted by {@code lift}
     * blocks. A block display spans its unit cube from its corner, so the turn has to happen round the block's
     * centre; an item display is centred on its origin already.
     */
    static Transformation pose(Part part, float spin, float lift) {
        float angle = part.still() ? 0 : spin;
        var rotation = new Quaternionf().rotateY(angle + (float) Math.toRadians(part.yaw()))
                .rotateZ((float) Math.toRadians(part.roll()));
        var centre = new Quaternionf().rotateY(angle).transform(new Vector3f(part.at()))
                .add(0, part.still() ? 0 : lift, 0);
        if (part.look() instanceof BlockLook) {
            var half = new Vector3f(part.size()).mul(BLOCK_CENTRE);
            centre.sub(new Quaternionf(rotation).transform(half));
        }
        return new Transformation(centre, rotation, new Vector3f(part.size()), new Quaternionf());
    }
}
