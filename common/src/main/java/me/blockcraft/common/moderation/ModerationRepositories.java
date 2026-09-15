package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

/** Moderation repositories sharing the database initialized by the runtime plugin. */
public record ModerationRepositories(PlayerRepository players, PunishmentRepository punishments, StrikeRepository strikes,
                                     WarnRepository warns, SettingsRepository settings, AdLogRepository adLog,
                                     VanishRepository vanish, FreezeRepository frozen, WatchRepository watches) {

    public static ModerationRepositories create(Database db) {
        return new ModerationRepositories(new PlayerRepository(db), new PunishmentRepository(db), new StrikeRepository(db),
                new WarnRepository(db), new SettingsRepository(db), new AdLogRepository(db), new VanishRepository(db),
                new FreezeRepository(db), new WatchRepository(db));
    }

}
