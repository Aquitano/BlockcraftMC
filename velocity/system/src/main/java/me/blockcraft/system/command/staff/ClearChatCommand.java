package me.blockcraft.system.command.staff;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.List;

/** Clears the chat of everyone on the sender's current server. */
public final class ClearChatCommand {

    private static final int BLANK_LINES = 200;

    private final CommandSupport support;
    private final Messages messages;

    public ClearChatCommand(CommandSupport support, Messages messages) {
        this.support = support;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("cc")
                .requires(CommandSupport.permission(SystemPermissions.CLEAR_CHAT))
                .executes(support.asPlayer((player, context) -> clear(player))));
    }

    private void clear(Player player) {
        Collection<Player> audience = player.getCurrentServer().map(ServerConnection::getServer)
                .map(RegisteredServer::getPlayersConnected).orElse(List.of(player));
        var blank = Component.space();
        for (int i = 0; i < BLANK_LINES; i++) {
            audience.forEach(other -> other.sendMessage(blank));
        }
        var cleared = messages.get("cc.cleared");
        audience.forEach(other -> other.sendMessage(cleared));
    }
}
