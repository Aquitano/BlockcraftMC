package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.CommandExecuteEvent.CommandResult;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;

import java.util.Locale;

/**
 * Answers /help with the network help and keeps the configured commands from players without the bypass: they are
 * denied when run and left out of the command tree the client completes from. A blocked command with lines under
 * {@code blocked-replies} answers with one of them instead of the plain refusal.
 */
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
        String command = event.getCommand().strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (command.equals(HELP)) {
            event.setResult(CommandResult.denied());
            messages.getLines("help").forEach(player::sendMessage);
            return;
        }
        if (config.isBlockedCommand(command) && !canBypass(player)) {
            event.setResult(CommandResult.denied());
            String reply = "blocked-replies." + command.substring(command.lastIndexOf(':') + 1);
            player.sendMessage(messages.has(reply) ? messages.pick(reply) : messages.get("command-blocked"));
        }
    }

    @Subscribe
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        if (!canBypass(event.getPlayer())) {
            event.getRootNode().getChildren().removeIf(node -> config.isBlockedCommand(node.getName()));
        }
    }

    private static boolean canBypass(Player player) {
        return player.hasPermission(SystemPermissions.BLOCKED_COMMANDS_BYPASS);
    }
}
