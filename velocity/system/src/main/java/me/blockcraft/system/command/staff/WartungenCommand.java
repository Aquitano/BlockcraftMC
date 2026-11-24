package me.blockcraft.system.command.staff;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.ProxySettings;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maintenance mode: announced over a minute (or immediately with "now"), toggled off by running it again.
 * Running it during the announcement cancels the pending switch instead.
 */
public final class WartungenCommand {

    private final Object plugin;
    private final CommandSupport support;
    private final ProxyServer proxy;
    private final ProxySettings settings;
    private final Messages messages;
    private final List<ScheduledTask> pending = new ArrayList<>();

    public WartungenCommand(Object plugin, CommandSupport support, ProxyServer proxy, ProxySettings settings, Messages messages) {
        this.plugin = plugin;
        this.support = support;
        this.proxy = proxy;
        this.settings = settings;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("wartungen")
                .requires(CommandSupport.permission(SystemPermissions.MAINTENANCE))
                .executes(support.run((source, context) -> toggle(false)))
                .then(BrigadierCommand.literalArgumentBuilder("now")
                        .executes(support.run((source, context) -> toggle(true)))));
    }

    private synchronized void toggle(boolean now) {
        if (settings.maintenance()) {
            settings.setMaintenance(false);
            proxy.sendMessage(messages.get("wartungen.disabled"));
            return;
        }
        if (now) {
            enable(messages.get("wartungen.enabled-now"));
            return;
        }
        if (!pending.isEmpty()) {
            cancelPending();
            proxy.sendMessage(messages.get("wartungen.cancelled"));
            return;
        }
        pending.addAll(MinuteCountdown.start(plugin, proxy, messages, "wartungen", () -> enable(messages.get("wartungen.enabled"))));
    }

    private synchronized void cancelPending() {
        pending.forEach(ScheduledTask::cancel);
        pending.clear();
    }

    private synchronized void enable(Component announcement) {
        cancelPending();
        settings.setMaintenance(true);
        proxy.sendMessage(announcement);
        var screen = messages.getJoined("wartungen.kick-screen");
        for (Player player : proxy.getAllPlayers()) {
            if (!player.hasPermission(SystemPermissions.MAINTENANCE_JOIN)) {
                player.disconnect(screen);
            }
        }
    }
}
