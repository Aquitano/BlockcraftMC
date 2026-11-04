package me.blockcraft.knockbackffa;

import java.util.Locale;
import java.util.random.RandomGenerator;

enum FfaKit {
    STANDARD, HEAVY, ARCHER, ANGLER, SNOWMAN, GRAPPLE, ENDERMAN;

    FfaKit next(RandomGenerator random) {
        var kits = values();
        int pick = random.nextInt(kits.length - 1);
        return kits[pick >= ordinal() ? pick + 1 : pick];
    }

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
