package me.blockcraft.sternenfront.appearance;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bone.RenderedBone;
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
import java.util.function.Predicate;

/**
 * Bots whose bodies BetterModel draws and animates, from the models resourcepack/RigModels.py writes, instead of a
 * {@link BodyRig}. The {@code appearance.animated-bodies} list in config.yml chooses which. BetterModel plays idle, walk
 * and death by itself. Its parts exist only as packets and a tracker has one glow for all its viewers, so each body is
 * two trackers: an outlined model for viewers who get an outline by the rules of {@link TargetOutlines}, and a plain one
 * for everyone else. A body's markings and a leader's pauldrons are bones of their own, which take the unit colour as a tint.
 */
final class AnimatedBodies implements AutoCloseable {

    private static final String OUTLINED = "_outlined";
    private static final int ENEMY_OUTLINE = 0xAA0000;
    private static final String MARKS = "_marks";
    private static final String PAULDRON = "_pauldron";

    private final Set<String> families;
    private final BiPredicate<Player, LivingEntity> outlined;
    private final int hurtTint;
    private final Map<UUID, Trackers> bodies = new HashMap<>();

    private static final class Trackers {
        private final LivingEntity actor;
        private final Shown plain;
        private final Shown outlined;
        private Markings markings;
        private int calmFrom;

        Trackers(LivingEntity actor, Shown plain, Shown outlined, Markings markings) {
            this.actor = actor;
            this.plain = plain;
            this.outlined = outlined;
            update(TrackerUpdateAction.tint(markings.colour()), AnimatedBodies::marking);
            update(TrackerUpdateAction.togglePart(markings.leader()), AnimatedBodies::pauldron);
            this.markings = markings;
        }

        /**
         * The hurt flash tints every bone and then restores the tint each had before, so a colour set during the flash
         * would come back as the red it replaced; a new colour waits for the flash to pass.
         */
        void keep(Markings wanted, int tick) {
            if (wanted.leader() != markings.leader()) {
                update(TrackerUpdateAction.togglePart(wanted.leader()), AnimatedBodies::pauldron);
                markings = new Markings(markings.colour(), wanted.leader());
            }
            if (wanted.colour() != markings.colour() && tick >= calmFrom) {
                update(TrackerUpdateAction.tint(wanted.colour()), AnimatedBodies::marking);
                markings = wanted;
            }
        }

        private void update(TrackerUpdateAction action, Predicate<RenderedBone> bones) {
            plain.tracker().update(action, bones);
            outlined.tracker().update(action, bones);
        }

        void close() {
            plain.tracker().close();
            outlined.tracker().close();
        }
    }

    private static boolean marking(RenderedBone bone) {
        return bone.name().name().endsWith(MARKS) || pauldron(bone);
    }

    private static boolean pauldron(RenderedBone bone) {
        return bone.name().name().endsWith(PAULDRON);
    }

    private record Shown(EntityTracker tracker) {

        void ifVisible(boolean visible, PlatformPlayer viewer) {
            if (visible) {
                tracker.show(viewer);
            } else {
                tracker.hide(viewer);
            }
        }
    }

    AnimatedBodies(Collection<String> families, BiPredicate<Player, LivingEntity> outlined, int hurtTint) {
        this.families = Set.copyOf(families);
        this.outlined = outlined;
        this.hurtTint = hurtTint;
    }

    boolean draws(RigBody body) {
        return families.contains(body.family());
    }

    void keep(LivingEntity actor, RigBody body, Markings markings, double recoil, Collection<? extends Player> viewers) {
        var trackers = bodies.computeIfAbsent(actor.getUniqueId(), id -> spawn(actor, body, markings));
        trackers.keep(markings, actor.getServer().getCurrentTick());
        for (var viewer : viewers) {
            var platform = BukkitAdapter.adapt(viewer);
            boolean showOutlined = outlined.test(viewer, actor);
            trackers.outlined.ifVisible(showOutlined, platform);
            trackers.plain.ifVisible(!showOutlined, platform);
        }
        if (recoil >= 1) {
            trackers.plain.tracker().animate("shoot", AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
            trackers.outlined.tracker().animate("shoot", AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
        }
    }

    void hurt(LivingEntity actor) {
        var trackers = bodies.get(actor.getUniqueId());
        if (trackers != null) {
            trackers.calmFrom = actor.getServer().getCurrentTick() + BodyRig.HURT_TICKS + 2;
        }
    }

    private Trackers spawn(LivingEntity actor, RigBody body, Markings markings) {
        var entity = BukkitAdapter.adapt(actor);
        var plain = BetterModel.model(body.family()).orElseThrow().getOrCreate(entity);
        var glowing = BetterModel.model(body.family() + OUTLINED).orElseThrow().create(entity, TrackerModifier.DEFAULT, tracker -> {
            tracker.update(TrackerUpdateAction.glow(true));
            tracker.update(TrackerUpdateAction.glowColor(ENEMY_OUTLINE));
        });
        plain.damageTintValue(hurtTint);
        glowing.damageTintValue(hurtTint);
        return new Trackers(actor, new Shown(plain), new Shown(glowing), markings);
    }

    /** A dead body keeps its trackers until BetterModel has played the death animation and closed them; nothing closes those of a bot removed alive. */
    void forgetAbsent(Set<UUID> present) {
        bodies.entrySet().removeIf(entry -> {
            if (present.contains(entry.getKey())) {
                return false;
            }
            if (entry.getValue().actor.getHealth() > 0) {
                entry.getValue().close();
            }
            return true;
        });
    }

    @Override
    public void close() {
        bodies.values().forEach(Trackers::close);
        bodies.clear();
    }
}
