package me.blockcraft.sternenfront.appearance;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.bukkit.platform.BukkitPlayer;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.TrackerModifier;
import kr.toxicity.model.api.tracker.TrackerUpdateAction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Bots whose bodies BetterModel draws and animates, from the models resourcepack/RigModels.py writes, instead of a
 * {@link BodyRig}. The {@code appearance.animated-bodies} list in config.yml chooses which. BetterModel plays idle, walk
 * and death by itself. Its parts exist only as packets and a tracker has one glow for all its viewers, so each body is
 * two trackers: an outlined model that enemies see and a plain one that everyone else sees.
 */
final class AnimatedBodies implements AutoCloseable {

    private static final String OUTLINED = "_outlined";
    private static final int ENEMY_OUTLINE = 0xAA0000;

    private final Set<String> families;
    private final BiPredicate<Player, LivingEntity> enemies;
    private final Map<UUID, List<EntityTracker>> bodies = new HashMap<>();

    AnimatedBodies(Collection<String> families, BiPredicate<Player, LivingEntity> enemies) {
        this.families = Set.copyOf(families);
        this.enemies = enemies;
    }

    boolean draws(RigBody body) {
        return families.contains(body.family());
    }

    void keep(LivingEntity actor, RigBody body, double recoil) {
        var trackers = bodies.computeIfAbsent(actor.getUniqueId(), id -> spawn(actor, body));
        if (recoil >= 1) {
            trackers.forEach(tracker -> tracker.animate("shoot", AnimationModifier.DEFAULT_WITH_PLAY_ONCE));
        }
    }

    private List<EntityTracker> spawn(LivingEntity actor, RigBody body) {
        var entity = BukkitAdapter.adapt(actor);
        var plain = BetterModel.model(body.family()).orElseThrow().getOrCreate(entity);
        var outlined = BetterModel.model(body.family() + OUTLINED).orElseThrow().create(entity, TrackerModifier.DEFAULT, tracker -> {
            tracker.update(TrackerUpdateAction.glow(true));
            tracker.update(TrackerUpdateAction.glowColor(ENEMY_OUTLINE));
        });
        plain.getPipeline().viewFilter(viewer -> !enemies.test(((BukkitPlayer) viewer).source(), actor));
        outlined.getPipeline().viewFilter(viewer -> enemies.test(((BukkitPlayer) viewer).source(), actor));
        return List.of(plain, outlined);
    }

    /** A dead body keeps its trackers until BetterModel has played the death animation and closed them. */
    void forgetAbsent(Set<UUID> present) {
        bodies.keySet().retainAll(present);
    }

    @Override
    public void close() {
        bodies.values().forEach(trackers -> trackers.forEach(EntityTracker::close));
        bodies.clear();
    }
}
