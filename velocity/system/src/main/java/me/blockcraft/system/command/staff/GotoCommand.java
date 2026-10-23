package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerLookup;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.network.ServerDirectory;

public final class GotoCommand {

    private final CommandSupport support;
    private final PlayerLookup lookup;
    private final ServerDirectory directory;
    private final Messages messages;

    public GotoCommand(CommandSupport support, PlayerLookup lookup, ServerDirectory directory, Messages messages) {
        this.support = support;
        this.lookup = lookup;
        this.directory = directory;
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
        var offline = messages.get("goto.offline");
        lookup.online(player, name, offline).ifPresent(target -> target.getCurrentServer().map(ServerConnection::getServer).ifPresentOrElse(
                server -> {
                    // Staff go even where it is full; the seat only keeps a waiting round from overfilling meanwhile.
                    directory.enter(player.getUniqueId(), server.getServerInfo().getName());
                    directory.send(player, server);
                },
                () -> player.sendMessage(offline)));
    }
}
