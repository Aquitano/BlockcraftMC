package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.game.Faction;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PostHologram implements AutoCloseable {

    private static final int SEGMENTS = 12;
    private final Hologram hologram;
    private final ItemDisplay emblem;
    private final List<ItemDisplay> ring = new ArrayList<>();
    private String faction = "neutral";
    private final List<String> segmentFactions = new ArrayList<>();

    public PostHologram(Plugin plugin, Location center) {
        hologram = new Hologram(plugin, center);
        var at = center.clone();
        at.setYaw(0);
        at.setPitch(0);
        var projector = hologram.part("projector", at.clone().add(0, .5, 0));
        projector.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.8f, 1, 1.8f), new Quaternionf()));
        emblem = hologram.part("emblem_neutral", at);
        for (int index = 0; index < SEGMENTS; index++) {
            ring.add(hologram.part("segment_neutral", at));
            segmentFactions.add("neutral");
        }
        hologram.refresh();
    }

    public void tick(long tick, Optional<Faction> owner, Optional<Faction> leaning, double share, boolean contested) {
        if (tick % 4 != 0) {
            return;
        }
        var color = owner.map(Faction::key).orElse("neutral");
        if (!faction.equals(color)) {
            faction = color;
            emblem.setItemStack(Hologram.model("emblem_" + color));
        }
        float pulse = contested ? 1 + .1f * (float) Math.sin(tick * .3) : 1;
        transform(emblem, new Vector3f(0, 1.9f + .05f * (float) Math.sin(tick * .06), 0),
                new Quaternionf().rotateY(tick * .018f), new Vector3f(1.25f * pulse));
        int filled = (int) Math.round(Math.clamp(share, 0, 1) * SEGMENTS);
        for (int index = 0; index < SEGMENTS; index++) {
            double angle = index * Math.PI * 2 / SEGMENTS + tick * .008;
            var segment = ring.get(index);
            var tint = index < filled ? leaning.map(Faction::key).orElse("neutral") : "neutral";
            if (!tint.equals(segmentFactions.get(index))) {
                segment.setItemStack(Hologram.model("segment_" + tint));
                segmentFactions.set(index, tint);
            }
            transform(segment, new Vector3f((float) Math.cos(angle) * 1.15f, 1.05f, (float) Math.sin(angle) * 1.15f),
                    new Quaternionf().rotateY((float) -angle + (float) Math.PI / 2), new Vector3f(index < filled ? pulse : .65f));
        }
        hologram.refresh();
    }

    private static void transform(ItemDisplay display, Vector3f translation, Quaternionf rotation, Vector3f scale) {
        display.setInterpolationDelay(0);
        display.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf()));
    }

    @Override
    public void close() {
        hologram.close();
    }
}
