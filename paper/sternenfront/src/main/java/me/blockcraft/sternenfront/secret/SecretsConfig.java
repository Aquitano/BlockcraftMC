package me.blockcraft.sternenfront.secret;

import org.bukkit.configuration.ConfigurationSection;

/** The {@code secrets} section of Sternenfront's config.yml. */
record SecretsConfig(int coins, int brummiHits, int brummiHideSeconds, int stillSeconds, int rumbleSeconds,
                     int restSeconds, float beastScale, int parSeconds, int limitSeconds, int mayTheFourthFactor) {

    static SecretsConfig from(ConfigurationSection config) {
        return new SecretsConfig(
                Math.max(0, config.getInt("secrets.coins", 50)),
                Math.max(1, config.getInt("secrets.brummi.hits", 3)),
                Math.max(1, config.getInt("secrets.brummi.hide-seconds", 30)),
                Math.max(2, config.getInt("secrets.sand-beast.still-seconds", 10)),
                Math.max(1, config.getInt("secrets.sand-beast.rumble-seconds", 4)),
                Math.max(1, config.getInt("secrets.sand-beast.rest-seconds", 60)),
                (float) Math.max(1, config.getDouble("secrets.sand-beast.scale", 5)),
                Math.max(1, config.getInt("secrets.kessel.par-seconds", 10)),
                Math.max(10, config.getInt("secrets.kessel.limit-seconds", 60)),
                Math.max(1, config.getInt("secrets.may-the-fourth.coin-factor", 2)));
    }
}
