package me.blockcraft.system.command.staff;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.ProxySettings;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;

/** Network-wide chat mute; the Paper servers enforce it from bc_settings. */
public final class GlobalMuteCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final ProxySettings settings;
    private final Messages messages;

    public GlobalMuteCommand(CommandSupport support, ProxyServer proxy, ProxySettings settings, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.settings = settings;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("globalmute")
                .requires(CommandSupport.permission(SystemPermissions.GLOBAL_MUTE))
                .executes(support.run((source, context) -> {
                    boolean enabled = !settings.globalMute();
                    settings.setGlobalMute(enabled);
                    proxy.sendMessage(messages.get(enabled ? "globalmute.enabled" : "globalmute.disabled"));
                })));
    }
}
