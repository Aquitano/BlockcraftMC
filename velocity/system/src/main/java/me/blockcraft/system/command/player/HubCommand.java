package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.command.CommandSupport;

public final class HubCommand {

    private final CommandSupport support;
    private final LobbyConnector lobby;

    public HubCommand(CommandSupport support, LobbyConnector lobby) {
        this.support = support;
        this.lobby = lobby;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("hub")
                .executes(support.asPlayer((player, context) -> lobby.send(player))));
    }
}
