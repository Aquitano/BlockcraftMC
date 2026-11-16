package me.blockcraft.system.report;

import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.report.ReportRepository;
import me.blockcraft.common.settings.PlayerSettingRepository;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.PlayerVisibility;
import org.slf4j.Logger;

import java.time.Duration;

public record Reports(ReportCommand command, ReportService service) {

    public static Reports load(ProxyServer proxy, Logger logger, Messages messages, Duration cooldown,
                               Database database, PlayerLookup lookup, PlayerVisibility visibility, PlayerChoices choices,
                               PlayerSettingRepository settings) {
        var preferences = new NotifyPreferences(choices, settings);
        var service = new ReportService(proxy, logger, new ReportRepository(database), preferences, messages, cooldown,
                lookup, visibility);
        return new Reports(new ReportCommand(preferences, messages, visibility), service);
    }
}
