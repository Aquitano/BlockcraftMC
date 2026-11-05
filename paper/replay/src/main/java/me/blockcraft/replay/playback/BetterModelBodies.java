package me.blockcraft.replay.playback;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.entity.BaseBukkitEntity;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import me.blockcraft.api.replay.RecordedModel;
import org.bukkit.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class BetterModelBodies implements BodyModels {

    private static final String WALK = "walk";
    private static final String SHOOT = "shoot";
    /** Samples arrive every few ticks, so a body that moves stands still between them without having stopped. */
    private static final int STILL_BEFORE_STOPPING = 6;

    private final Map<UUID, Drawn> drawn = new HashMap<>();

    private static final class Drawn {
        private final EntityTracker tracker;
        private int still = STILL_BEFORE_STOPPING;
        private boolean walking;

        Drawn(EntityTracker tracker) {
            this.tracker = tracker;
        }

        void walk(boolean walking) {
            if (walking != this.walking) {
                this.walking = walking;
                if (walking) {
                    tracker.animate(WALK);
                } else {
                    tracker.stopAnimation(WALK);
                }
            }
        }
    }

    @Override
    public void clean(Entity entity) {
        entity.getPersistentDataContainer().remove(BaseBukkitEntity.TRACKING_ID);
    }

    @Override
    public void draw(Entity entity) {
        RecordedModel.of(entity).flatMap(BetterModel::model).ifPresent(model ->
                drawn.put(entity.getUniqueId(), new Drawn(model.getOrCreate(BukkitAdapter.adapt(entity)))));
    }

    /** A frozen mob has no path, which is how BetterModel would notice that it walks. */
    @Override
    public void moved(Entity entity, boolean moved) {
        var body = drawn.get(entity.getUniqueId());
        if (body != null) {
            body.still = moved ? 0 : body.still + 1;
            body.walk(body.still < STILL_BEFORE_STOPPING);
        }
    }

    @Override
    public void fired(Entity entity) {
        var body = drawn.get(entity.getUniqueId());
        if (body != null) {
            body.tracker.animate(SHOOT, AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
        }
    }

    @Override
    public void died(Entity entity) {
        drawn.remove(entity.getUniqueId());
    }

    @Override
    public void gone(Entity entity) {
        var body = drawn.remove(entity.getUniqueId());
        if (body != null) {
            body.tracker.close();
        }
    }

    @Override
    public void hold() {
        drawn.values().forEach(body -> body.walk(false));
    }
}
