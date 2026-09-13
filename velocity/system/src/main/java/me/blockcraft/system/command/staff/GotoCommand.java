package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;

public final class GotoCommand {

    private final CommandSupport support;
    private final PlayerVisibility visibility;
    private final Messages messages;

    public GotoCommand(CommandSupport support, PlayerVisibility visibility, Messages messages) {
        this.support = support;
        this.visibility = visibility;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("goto")
                .requires(CommandSupport.permission(SystemPermissions.GOTO))
                .executes(support.usage("goto.usage"))
                .then(support.playerArgument("player")
                        .executes(support.asPlayer((player, context) -> follow(player, StringArgumentType.getString(context, "player"))))));
    }

    private void follow(Player player, String name) {
        visibility.find(player, name).flatMap(Player::getCurrentServer).map(ServerConnection::getServer).ifPresentOrElse(
                server -> player.createConnectionRequest(server).fireAndForget(),
                () -> player.sendMessage(messages.get("goto.offline")));
    }
}
