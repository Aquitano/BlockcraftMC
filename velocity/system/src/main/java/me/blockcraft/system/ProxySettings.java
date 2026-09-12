package me.blockcraft.system;

import me.blockcraft.common.moderation.Setting;
import me.blockcraft.common.moderation.SettingsRepository;

/**
 * Maintenance and global mute are only switched from the proxy, so the proxy keeps them in memory
 * and mirrors every change to bc_settings for the Paper servers.
 */
public final class ProxySettings {

    private final SettingsRepository settings;
    private volatile boolean maintenance;
    private volatile boolean globalMute;

    public ProxySettings(SettingsRepository settings) {
        this.settings = settings;
        this.maintenance = settings.isEnabled(Setting.MAINTENANCE);
        this.globalMute = settings.isEnabled(Setting.GLOBAL_MUTE);
    }

    public boolean maintenance() {
        return maintenance;
    }

    public void setMaintenance(boolean enabled) {
        maintenance = enabled;
        settings.setEnabled(Setting.MAINTENANCE, enabled);
    }

    public boolean globalMute() {
        return globalMute;
    }

    public void setGlobalMute(boolean enabled) {
        globalMute = enabled;
        settings.setEnabled(Setting.GLOBAL_MUTE, enabled);
    }
}
