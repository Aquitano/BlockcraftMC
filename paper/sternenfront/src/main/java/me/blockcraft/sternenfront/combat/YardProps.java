package me.blockcraft.sternenfront.combat;

import me.blockcraft.sternenfront.appearance.Displays;
import me.blockcraft.sternenfront.arena.YardEnvironment;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class YardProps {

    private final List<YardEnvironment.Prop> placements;
    private final Map<YardEnvironment.Prop, ItemDisplay> displays = new LinkedHashMap<>();
    private final double rangeSquared;
    private final float viewRange;

    YardProps(SternenfrontGame game) {
        placements = game.map().environment().props();
        double range = Math.clamp(game.plugin().getConfig().getDouble("presentation.shipyard.props-range"), 16, 128);
        rangeSquared = range * range;
        viewRange = (float) (range / 64);
    }

    void tick(long tick) {
        if (tick % 20 != 0) return;
        for (var prop : placements) {
            var at = prop.at();
            var world = at.getWorld();
            var display = displays.get(prop);
            boolean nearby = world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)
                    && world.getPlayers().stream().anyMatch(player -> player.getLocation().distanceSquared(at) <= rangeSquared);
            if (!nearby) {
                if (display != null) {
                    display.remove();
                    displays.remove(prop);
                }
            } else if (display == null || !display.isValid()) {
                displays.put(prop, Displays.item(at, prop.model().item().build(), spawned -> {
                    // Minecraft adds a half turn to item displays after this transform.
                    spawned.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(prop.scale()),
                            new Quaternionf().rotationY((float) Math.PI)));
                    spawned.setDisplayWidth(3 * prop.scale());
                    spawned.setDisplayHeight(2 * prop.scale());
                    spawned.setViewRange(viewRange);
                }));
            }
        }
    }

    void clear() {
        displays.values().forEach(Entity::remove);
        displays.clear();
    }
}
