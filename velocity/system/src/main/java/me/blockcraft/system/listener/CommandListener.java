package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.CommandExecuteEvent.CommandResult;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;

/** Answers /help with the network help and blocks the configured commands for players without the bypass. */
public final class CommandListener {

    private static final String HELP = "help";

    private final SystemConfig config;
    private final Messages messages;

    public CommandListener(SystemConfig config, Messages messages) {
        this.config = config;
        this.messages = messages;
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }
        String command = event.getCommand().strip();
        if (command.equalsIgnoreCase(HELP)) {
            event.setResult(CommandResult.denied());
            messages.getLines("help").forEach(player::sendMessage);
            return;
        }
        if (config.isBlockedCommand(command) && !player.hasPermission(SystemPermissions.BLOCKED_COMMANDS_BYPASS)) {
            event.setResult(CommandResult.denied());
            player.sendMessage(messages.get("command-blocked"));
        }
    }
}
