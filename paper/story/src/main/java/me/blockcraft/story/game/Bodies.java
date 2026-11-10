package me.blockcraft.story.game;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.util.function.FloatSupplier;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;

import java.util.Optional;

/**
 * The BetterModel bodies the story's cast wears, and how their animations blend: an idle underneath, a walk over it,
 * talk over that and a gesture on top, each easing into its first pose instead of jumping there.
 */
public final class Bodies {

    /**
     * BetterModel hides the entity under a body by sending it as invisible, which a spectator, and so every cutscene
     * viewer, still sees as a translucent figure, and a client that learns of the entity before that reaches it sees it
     * whole for a moment. A skin with nothing drawn on it leaves nothing to see either way.
     */
    private static final Key BLANK_SKIN = Key.key("blockcraft", "entity/story/blank");
    private static final AnimationModifier IDLE = AnimationModifier.builder().start(6).build();
    private static final AnimationModifier TALK = AnimationModifier.builder().start(5).priority(2).build();
    private static final AnimationModifier GESTURE = AnimationModifier.builder().start(4).priority(3).build();
    private static final String TALKING = "talk";
    private static final String WALKING = "walk";

    private Bodies() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("BetterModel");
    }

    /** Puts {@code model} on {@code entity}; its body turns after its head a few ticks later, as a person's does. */
    public static Optional<EntityTracker> dress(LivingEntity entity, String model) {
        if (model.isBlank() || !available()) return Optional.empty();
        return BetterModel.model(model).map(blueprint -> {
            var body = blueprint.getOrCreate(BukkitAdapter.adapt(entity));
            body.bodyRotator().setValue(rotation -> {
                rotation.setRotationDelay(3);
                rotation.setRotationDuration(8);
            });
            if (entity instanceof Mannequin mannequin) {
                mannequin.setProfile(ResolvableProfile.resolvableProfile().skinPatch(skin -> skin.body(BLANK_SKIN)).build());
            }
            return body;
        });
    }

    public static void idle(EntityTracker body, String animation) {
        body.animate(animation, IDLE);
    }

    /** Walks with the legs' pace at {@code pace} times the walk's own, so the feet keep up with the ground. */
    public static void walk(EntityTracker body, FloatSupplier pace) {
        body.animate(WALKING, AnimationModifier.builder().start(4).priority(1).speed(pace).build());
    }

    public static void stopWalking(EntityTracker body) {
        body.stopAnimation(WALKING);
    }

    public static void talk(EntityTracker body, boolean talking) {
        if (talking) {
            body.animate(TALKING, TALK);
        } else {
            body.stopAnimation(TALKING);
        }
    }

    public static void gesture(EntityTracker body, String animation) {
        body.animate(animation, GESTURE);
    }
}
