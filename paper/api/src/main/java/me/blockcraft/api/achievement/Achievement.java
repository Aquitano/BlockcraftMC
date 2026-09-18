package me.blockcraft.api.achievement;

/**
 * One entry of the catalog in {@code achievements} of BlockcraftAPI's config.yml: earned once {@code metric} of
 * {@code game} reaches {@code threshold}, kept as the unlock {@link #item()} and paid with {@code xp}. The name and
 * description are {@code achievements.<game>.<name>.name} and {@code .description} in messages.yml.
 */
public record Achievement(String game, String name, Metric metric, int threshold, int xp) {

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
        return current >= threshold;
    }
}
