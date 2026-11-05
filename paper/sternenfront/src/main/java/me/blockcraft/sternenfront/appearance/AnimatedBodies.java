package me.blockcraft.sternenfront.appearance;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.platform.PlatformPlayer;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.TrackerModifier;
import kr.toxicity.model.api.tracker.TrackerUpdateAction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Bots whose bodies BetterModel draws and animates, from the models resourcepack/RigModels.py writes, instead of a
 * {@link BodyRig}. The {@code appearance.animated-bodies} list in config.yml chooses which. BetterModel plays idle, walk
 * and death by itself. Its parts exist only as packets and a tracker has one glow for all its viewers, so each body is
 * two trackers: an outlined model for viewers who get an outline by the rules of {@link TargetOutlines}, and a plain one
 * for everyone else.
 */
final class AnimatedBodies implements AutoCloseable {

    private static final String OUTLINED = "_outlined";
    private static final int ENEMY_OUTLINE = 0xAA0000;

    private final Set<String> families;
    private final BiPredicate<Player, LivingEntity> outlined;
    private final Map<UUID, Trackers> bodies = new HashMap<>();

    private record Trackers(Shown plain, Shown outlined) { }

    private record Shown(EntityTracker tracker) {

        void ifVisible(boolean visible, PlatformPlayer viewer) {
            if (visible) {
                tracker.show(viewer);
            } else {
                tracker.hide(viewer);
            }
        }
    }

    AnimatedBodies(Collection<String> families, BiPredicate<Player, LivingEntity> outlined) {
        this.families = Set.copyOf(families);
        this.outlined = outlined;
    }

    boolean draws(RigBody body) {
        return families.contains(body.family());
    }

    void keep(LivingEntity actor, RigBody body, double recoil, Collection<? extends Player> viewers) {
        var trackers = bodies.computeIfAbsent(actor.getUniqueId(), id -> spawn(actor, body));
        for (var viewer : viewers) {
            var platform = BukkitAdapter.adapt(viewer);
            boolean showOutlined = outlined.test(viewer, actor);
            trackers.outlined().ifVisible(showOutlined, platform);
            trackers.plain().ifVisible(!showOutlined, platform);
        }
        if (recoil >= 1) {
            trackers.plain().tracker().animate("shoot", AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
            trackers.outlined().tracker().animate("shoot", AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
        }
    }

    private Trackers spawn(LivingEntity actor, RigBody body) {
        var entity = BukkitAdapter.adapt(actor);
        var plain = BetterModel.model(body.family()).orElseThrow().getOrCreate(entity);
        var glowing = BetterModel.model(body.family() + OUTLINED).orElseThrow().create(entity, TrackerModifier.DEFAULT, tracker -> {
            tracker.update(TrackerUpdateAction.glow(true));
            tracker.update(TrackerUpdateAction.glowColor(ENEMY_OUTLINE));
        });
        return new Trackers(new Shown(plain), new Shown(glowing));
    }

    /** A dead body keeps its trackers until BetterModel has played the death animation and closed them. */
    void forgetAbsent(Set<UUID> present) {
        bodies.keySet().retainAll(present);
    }

    @Override
    public void close() {
        bodies.values().forEach(trackers -> {
            trackers.plain().tracker().close();
            trackers.outlined().tracker().close();
        });
        bodies.clear();
    }
}
