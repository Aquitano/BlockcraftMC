package me.blockcraft.api.replay;

import org.bukkit.entity.Entity;

import java.util.Optional;

/**
 * Names the BetterModel model a game draws a mob with. That model exists only as packets, so a recording holds the bare
 * mob; the mark travels with the recorded entity and lets playback draw the model again.
 */
public final class RecordedModel {

    private static final String TAG = "blockcraft.model:";

    private RecordedModel() {
    }

    public static void mark(Entity entity, String model) {
        entity.addScoreboardTag(TAG + model);
    }

    public static Optional<String> of(Entity entity) {
        return entity.getScoreboardTags().stream().filter(tag -> tag.startsWith(TAG))
                .map(tag -> tag.substring(TAG.length())).findFirst();
    }
}
