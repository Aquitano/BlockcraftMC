package me.blockcraft.starwars;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import me.blockcraft.api.replay.RecordedModel;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SceneryActors implements AutoCloseable {
    private static final String TAG = "blockcraft.starwars.scenery";
    private static final Set<String> KINDS = Set.of("astromech", "gonk", "mouse", "bantha", "dewback", "womp_rat");
    private record Actor(ArmorStand entity, EntityTracker tracker) {}

    private final StarWarsPlugin plugin;
    private final List<Actor> actors = new ArrayList<>();
    private final Set<Chunk> chunks = new HashSet<>();

    SceneryActors(StarWarsPlugin plugin) {
        this.plugin = plugin;
    }

    void spawn() {
        close();
        var world = plugin.getServer().getWorld(plugin.getConfig().getString("scenery.world", "world"));
        if (world == null) return;
        world.getEntities().stream().filter(entity -> entity.getScoreboardTags().contains(TAG))
                .forEach(org.bukkit.entity.Entity::remove);
        for (var entry : plugin.getConfig().getMapList("scenery.exhibits")) {
            if (!(entry.get("kind") instanceof String kind) || !KINDS.contains(kind)
                    || !(entry.get("x") instanceof Number x) || !(entry.get("y") instanceof Number y)
                    || !(entry.get("z") instanceof Number z)) continue;
            if (!Double.isFinite(x.doubleValue()) || !Double.isFinite(y.doubleValue()) || !Double.isFinite(z.doubleValue())) continue;
            var model = BetterModel.model("kit_" + kind);
            if (model.isEmpty()) {
                plugin.getLogger().warning("Missing scenery model: kit_" + kind);
                continue;
            }
            float yaw = entry.get("yaw") instanceof Number value ? value.floatValue() : 180;
            if (!Float.isFinite(yaw)) continue;
            var at = new Location(world, x.doubleValue(), y.doubleValue(), z.doubleValue(), yaw, 0);
            var chunk = at.getChunk();
            if (chunks.add(chunk)) chunk.addPluginChunkTicket(plugin);
            var entity = world.spawn(at, ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                stand.setPersistent(false);
                stand.setCollidable(false);
                stand.addScoreboardTag(TAG);
            });
            var tracker = model.orElseThrow().getOrCreate(BukkitAdapter.adapt(entity));
            RecordedModel.mark(entity, "kit_" + kind);
            String animation = "walk".equals(entry.get("animation")) ? "walk" : "idle";
            tracker.animate(animation, AnimationModifier.DEFAULT);
            actors.add(new Actor(entity, tracker));
        }
    }

    @Override public void close() {
        actors.forEach(actor -> {
            actor.tracker.close();
            actor.entity.remove();
        });
        actors.clear();
        chunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        chunks.clear();
    }
}
