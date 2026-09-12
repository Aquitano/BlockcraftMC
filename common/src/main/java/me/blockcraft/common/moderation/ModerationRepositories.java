package me.blockcraft.common.moderation;

import me.blockcraft.common.db.Database;

/** Every moderation table in one place; both the proxy and the Paper servers create them at startup. */
public record ModerationRepositories(PlayerRepository players, PunishmentRepository punishments, StrikeRepository strikes,
                                     WarnRepository warns, SettingsRepository settings, AdLogRepository adLog) {

    public static ModerationRepositories create(Database db) {
        return new ModerationRepositories(new PlayerRepository(db), new PunishmentRepository(db), new StrikeRepository(db),
                new WarnRepository(db), new SettingsRepository(db), new AdLogRepository(db));
    }

    public void createTables() {
        players.createTable();
        punishments.createTable();
        strikes.createTable();
        warns.createTable();
        settings.createTable();
        adLog.createTable();
    }
}
