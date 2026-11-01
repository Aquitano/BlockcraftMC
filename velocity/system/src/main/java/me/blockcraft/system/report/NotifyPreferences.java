package me.blockcraft.system.report;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.common.settings.PlayerSettingRepository;
import me.blockcraft.system.PlayerChoices;


/**
 * Whether a staff member hears about new reports: the {@link PlayerSetting#REPORT_NOTIFICATIONS} toggle.
 */
public final class NotifyPreferences {

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

}
