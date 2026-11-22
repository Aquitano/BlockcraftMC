package me.blockcraft.sternenfront.bot;

/** How hard enemy bolts passing close by press a bot: each pass adds to it, and it fades between them. */
final class Suppression {

    private double level;
    private long at;

    /** A bolt passed {@code closeness} of the way from the edge of the near-miss radius to dead on. */
    void passed(double closeness, long tick, BotConfig.Shaken rules) {
        level = Math.min(rules.max(), level(tick, rules) + rules.nearMiss() * closeness);
        at = tick;
    }

    double level(long tick, BotConfig.Shaken rules) {
        return Math.max(0, level - (double) (tick - at) / rules.fadeTicks());
    }

    /** How many times its spread a bot's shots scatter under this much suppression. */
    double spread(long tick, BotConfig.Shaken rules) {
        return 1 + level(tick, rules) * rules.spread();
    }
}
