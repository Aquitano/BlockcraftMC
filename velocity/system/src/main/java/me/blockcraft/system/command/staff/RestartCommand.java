package me.blockcraft.system.command.staff;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Announces a restart over one minute, kicks everyone, then stops the proxy. */
public final class RestartCommand {

    private final Object plugin;
    private final CommandSupport support;
    private final ProxyServer proxy;
    private final Messages messages;
    private final AtomicBoolean pending = new AtomicBoolean();

    public RestartCommand(Object plugin, CommandSupport support, ProxyServer proxy, Messages messages) {
        this.plugin = plugin;
        this.support = support;
        this.proxy = proxy;
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
        MinuteCountdown.start(plugin, proxy, messages, "restart", () -> {
            var screen = messages.get("restart.kick-screen");
            for (Player player : proxy.getAllPlayers()) {
                player.disconnect(screen);
            }
            proxy.getScheduler().buildTask(plugin, () -> proxy.shutdown(screen)).delay(1, TimeUnit.SECONDS).schedule();
        });
    }
}
