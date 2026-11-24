package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.config.BundledDefaults;
import org.bukkit.configuration.ConfigurationSection;

/** The {@code secrets} section of Sternenfront's config.yml. */
record SecretsConfig(int coins, int brummiHits, int brummiHideSeconds, int stillSeconds, int rumbleSeconds,
                     int restSeconds, float beastScale, int parSeconds, int limitSeconds,
                     int inspectionParSeconds, int inspectionLimitSeconds, int mayTheFourthFactor) {

    static SecretsConfig from(ConfigurationSection config) {
        var read = BundledDefaults.strict(config);
        return new SecretsConfig(
                Math.max(0, read.integer("secrets.coins")),
                Math.max(1, read.integer("secrets.brummi.hits")),
                Math.max(1, read.integer("secrets.brummi.hide-seconds")),
                Math.max(2, read.integer("secrets.sand-beast.still-seconds")),
                Math.max(1, read.integer("secrets.sand-beast.rumble-seconds")),
                Math.max(1, read.integer("secrets.sand-beast.rest-seconds")),
                (float) Math.max(1, read.decimal("secrets.sand-beast.scale")),
                Math.max(1, read.integer("secrets.kessel.par-seconds")),
                Math.max(10, read.integer("secrets.kessel.limit-seconds")),
                Math.max(1, read.integer("secrets.inspection-course.par-seconds")),
                Math.max(10, read.integer("secrets.inspection-course.limit-seconds")),
                Math.max(1, read.integer("secrets.may-the-fourth.coin-factor")));
    }
}
