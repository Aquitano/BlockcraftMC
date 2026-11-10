package me.blockcraft.story.game;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;

import java.util.Optional;

/** The BetterModel bodies the story's cast wears. */
public final class Bodies {

    /**
     * BetterModel hides the entity under a body by sending it as invisible, which a spectator, and so every cutscene
     * viewer, still sees as a translucent figure, and a client that learns of the entity before that reaches it sees it
     * whole for a moment. A skin with nothing drawn on it leaves nothing to see either way.
     */
    private static final Key BLANK_SKIN = Key.key("blockcraft", "entity/story/blank");

    private Bodies() {
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("BetterModel");
    }

    public static Optional<EntityTracker> dress(LivingEntity entity, String model) {
        if (model.isBlank() || !available()) return Optional.empty();
        return BetterModel.model(model).map(blueprint -> {
            var body = blueprint.getOrCreate(BukkitAdapter.adapt(entity));
            if (entity instanceof Mannequin mannequin) {
                mannequin.setProfile(ResolvableProfile.resolvableProfile().skinPatch(skin -> skin.body(BLANK_SKIN)).build());
            }
            return body;
        });
    }
}
