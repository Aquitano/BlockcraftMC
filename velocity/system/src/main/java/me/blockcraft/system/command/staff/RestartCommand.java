package me.blockcraft.system.command.staff;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Announces a restart over one minute, kicks everyone, then stops the proxy. */
public final class RestartCommand {

    private final Object plugin;
    private final CommandSupport support;
    private final ProxyServer proxy;
    private final SystemConfig config;
    private final Messages messages;
    private final AtomicBoolean pending = new AtomicBoolean();

    public RestartCommand(Object plugin, CommandSupport support, ProxyServer proxy, SystemConfig config, Messages messages) {
        this.plugin = plugin;
        this.support = support;
        this.proxy = proxy;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("restart")
                .requires(CommandSupport.permission(SystemPermissions.RESTART))
                .executes(support.run((source, context) -> restart(source))));
    }

    private void restart(CommandSource source) {
        if (!pending.compareAndSet(false, true)) {
            source.sendMessage(messages.get("restart.already-pending"));
            return;
        }
        var scheduler = proxy.getScheduler();
        proxy.sendMessage(countdown("restart.countdown-60"));
        scheduler.buildTask(plugin, () -> proxy.sendMessage(countdown("restart.countdown-30"))).delay(30, TimeUnit.SECONDS).schedule();
        scheduler.buildTask(plugin, () -> proxy.sendMessage(countdown("restart.countdown-10"))).delay(50, TimeUnit.SECONDS).schedule();
        scheduler.buildTask(plugin, () -> proxy.sendMessage(countdown("restart.countdown-5"))).delay(55, TimeUnit.SECONDS).schedule();
        scheduler.buildTask(plugin, () -> {
            var screen = messages.get("restart.kick-screen");
            for (Player player : proxy.getAllPlayers()) {
                player.disconnect(screen);
            }
        }).delay(60, TimeUnit.SECONDS).schedule();
        scheduler.buildTask(plugin, () -> proxy.shutdown(messages.get("restart.kick-screen"))).delay(61, TimeUnit.SECONDS).schedule();
    }

    private Component countdown(String key) {
        return messages.get(key, Placeholder.unparsed("ip", config.network().ip()));
    }
}
