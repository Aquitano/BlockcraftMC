package me.blockcraft.system.command.staff;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.ProxySettings;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.concurrent.TimeUnit;

/** Maintenance mode: announced over a minute (or immediately with "now"), toggled off by running it again. */
public final class WartungenCommand {

    private final Object plugin;
    private final CommandSupport support;
    private final ProxyServer proxy;
    private final ProxySettings settings;
    private final SystemConfig config;
    private final Messages messages;

    public WartungenCommand(Object plugin, CommandSupport support, ProxyServer proxy, ProxySettings settings, SystemConfig config,
                            Messages messages) {
        this.plugin = plugin;
        this.support = support;
        this.proxy = proxy;
        this.settings = settings;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("wartungen")
                .requires(CommandSupport.permission(SystemPermissions.MAINTENANCE))
                .executes(support.run((source, context) -> toggle(false)))
                .then(BrigadierCommand.literalArgumentBuilder("now")
                        .executes(support.run((source, context) -> toggle(true)))));
    }

    private void toggle(boolean now) {
        if (settings.maintenance()) {
            settings.setMaintenance(false);
            proxy.sendMessage(messages.get("wartungen.disabled"));
            return;
        }
        if (now) {
            enable(messages.get("wartungen.enabled-now"));
            return;
        }
        var scheduler = proxy.getScheduler();
        proxy.sendMessage(countdown("wartungen.countdown-60"));
        scheduler.buildTask(plugin, () -> proxy.sendMessage(countdown("wartungen.countdown-30"))).delay(30, TimeUnit.SECONDS).schedule();
        scheduler.buildTask(plugin, () -> proxy.sendMessage(countdown("wartungen.countdown-10"))).delay(50, TimeUnit.SECONDS).schedule();
        scheduler.buildTask(plugin, () -> proxy.sendMessage(countdown("wartungen.countdown-5"))).delay(55, TimeUnit.SECONDS).schedule();
        scheduler.buildTask(plugin, () -> enable(countdown("wartungen.enabled"))).delay(60, TimeUnit.SECONDS).schedule();
    }

    private void enable(Component announcement) {
        settings.setMaintenance(true);
        proxy.sendMessage(announcement);
        var screen = Component.join(JoinConfiguration.newlines(), messages.getLines("wartungen.kick-screen", ip()));
        for (Player player : proxy.getAllPlayers()) {
            if (!player.hasPermission(SystemPermissions.MAINTENANCE_JOIN)) {
                player.disconnect(screen);
            }
        }
    }

    private Component countdown(String key) {
        return messages.get(key, ip());
    }

    private TagResolver ip() {
        return Placeholder.unparsed("ip", config.network().ip());
    }
}
