package me.blockcraft.system.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.CommandExecuteEvent.CommandResult;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.SystemPermissions;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers /help with the network help and keeps the configured commands from players without the bypass: they are
 * denied when run and left out of the command tree the client completes from.
 */
public final class CommandListener {

    private static final String HELP = "help";

    private final SystemConfig config;
    private final Messages messages;
    private final Map<UUID, Integer> pluginRequests = new ConcurrentHashMap<>();

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
        if (command.equalsIgnoreCase(HELP)) {
            event.setResult(CommandResult.denied());
            messages.getLines("help").forEach(player::sendMessage);
            return;
        }
        if (config.isBlockedCommand(command) && !canBypass(player)) {
            event.setResult(CommandResult.denied());
            String name = command.substring(command.lastIndexOf(':') + 1);
            if (name.equals("plugins")) {
                name = "pl";
            }
            String key = "command-blocked." + name;
            if (name.equals("pl") && pluginRequests.merge(player.getUniqueId(), 1, (count, ignored) -> Math.min(3, count + 1)) >= 3
                    && messages.has("command-blocked.pl-again")) {
                key = "command-blocked.pl-again";
            }
            if (messages.has(key)) {
                messages.getLines(key).forEach(player::sendMessage);
            } else {
                player.sendMessage(messages.get("command-blocked"));
            }
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        pluginRequests.remove(event.getPlayer().getUniqueId());
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
