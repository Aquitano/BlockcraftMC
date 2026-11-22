package me.blockcraft.zombies.game;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelScaler;
import me.blockcraft.api.replay.RecordedModel;
import org.bukkit.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The only class that touches BetterModel, so Zombies loads without it. */
final class BetterModelBodies implements Bodies {

    private final Map<UUID, EntityTracker> trackers = new HashMap<>();

    @Override
    public boolean draw(Mob mob, String model, double scale) {
        var renderer = BetterModel.model(model);
        if (renderer.isEmpty()) {
            return false;
        }
        var tracker = renderer.get().getOrCreate(BukkitAdapter.adapt(mob));
        tracker.scaler(ModelScaler.value((float) scale));
        RecordedModel.mark(mob, model);
        trackers.put(mob.getUniqueId(), tracker);
        return true;
    }

    @Override
    public void play(Mob mob, String animation) {
        var tracker = trackers.get(mob.getUniqueId());
        if (tracker == null || tracker.isClosed()) {
            trackers.remove(mob.getUniqueId());
            return;
        }
        tracker.animate(animation, AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
    }

    @Override
    public void clear() {
        trackers.values().forEach(EntityTracker::close);
        trackers.clear();
    }
}
