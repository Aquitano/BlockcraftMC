package me.blockcraft.system.report;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.common.settings.PlayerSettingRepository;
import me.blockcraft.system.PlayerChoices;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Whether a staff member hears about new reports: the {@link PlayerSetting#REPORT_NOTIFICATIONS} toggle. The old
 * plugin kept the opt-outs in {@code ignored-staff.yml}; that file is imported once and then renamed.
 */
public final class NotifyPreferences {

    static final String LEGACY_FILE = "ignored-staff.yml";
    static final String IMPORTED_SUFFIX = ".imported";
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyMessageCodec.CHANNEL);

    private final PlayerChoices choices;
    private final PlayerSettingRepository settings;

    public NotifyPreferences(PlayerChoices choices, PlayerSettingRepository settings) {
        this.choices = choices;
        this.settings = settings;
    }

    public boolean enabled(Player player) {
        return choices.enabled(player, PlayerSetting.REPORT_NOTIFICATIONS);
    }

    public void set(Player player, boolean enabled) {
        String value = PlayerSetting.value(enabled);
        settings.set(player.getUniqueId(), PlayerSetting.REPORT_NOTIFICATIONS, value);
        choices.set(player, PlayerSetting.REPORT_NOTIFICATIONS, value);
        var payload = ProxyMessageCodec.encode(new ProxyMessage.Setting(PlayerSetting.REPORT_NOTIFICATIONS, value));
        player.getCurrentServer().ifPresent(connection -> connection.sendPluginMessage(CHANNEL, payload));
    }

    public static void importLegacy(Path directory, PlayerSettingRepository settings) {
        Path file = directory.resolve(LEGACY_FILE);
        if (Files.notExists(file)) {
            return;
        }
        try {
            List<?> muted;
            try (InputStream in = Files.newInputStream(file)) {
                muted = YamlFile.load(in).get("muted") instanceof List<?> ids ? ids : List.of();
            }
            muted.forEach(id -> settings.set(UUID.fromString(String.valueOf(id)), PlayerSetting.REPORT_NOTIFICATIONS, PlayerSetting.OFF));
            Files.move(file, directory.resolve(LEGACY_FILE + IMPORTED_SUFFIX));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not import " + file, e);
        }
    }
}
