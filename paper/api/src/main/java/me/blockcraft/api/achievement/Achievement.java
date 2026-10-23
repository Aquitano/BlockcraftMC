package me.blockcraft.api.achievement;

import me.blockcraft.api.cosmetic.ChatTagChoice;
import me.blockcraft.api.cosmetic.CosmeticReward;

import java.util.Optional;

/**
 * One entry of the catalog in {@code achievements} of BlockcraftAPI's config.yml: earned once {@code metric} of
 * {@code game} reaches {@code threshold}, or for a moment once {@link Achievements#grant} names it, kept as the
 * unlock {@link #item()} and paid with {@code xp}. The name and description are
 * {@code achievements.<game>.<name>.name} and {@code .description} in messages.yml.
 */
public record Achievement(String game, String name, Metric metric, int threshold, int xp, Optional<CosmeticReward> cosmetic) {

    public Achievement(String game, String name, Metric metric, int threshold, int xp) {
        this(game, name, metric, threshold, xp, Optional.empty());
    }

    public Optional<ChatTagChoice> tag() {
        return cosmetic.flatMap(reward -> reward instanceof CosmeticReward.Tag(var choice)
                ? Optional.of(choice) : Optional.empty());
    }

    /** The game key of achievements that count the network level rather than a game's stats. */
    public static final String NETWORK = "network";

    public static final String ITEM_PREFIX = "achievement.";

    public String key() {
        return game + "." + name;
    }

    public String item() {
        return ITEM_PREFIX + key();
    }

    public boolean reached(long current) {
        return !isMoment() && current >= threshold;
    }

    public boolean isMoment() {
        return metric == Metric.MOMENT;
    }
}
