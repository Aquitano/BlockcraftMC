package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.network.ServerDirectory;

/**
 * Target of the /joinme click action: takes a seat on the named server, or lets the player watch its running round,
 * so that everyone clicking the same JoinMe at once cannot overfill it.
 */
public final class ServerJoinCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final ServerDirectory directory;
    private final Messages messages;

    public ServerJoinCommand(CommandSupport support, ProxyServer proxy, ServerDirectory directory, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.directory = directory;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("serverjoin")
                .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                        .executes(support.asPlayer((player, context) -> join(player, StringArgumentType.getString(context, "server"))))));
    }

    private void join(Player player, String name) {
        var server = proxy.getServer(name);
        if (server.isEmpty()) {
            player.sendMessage(messages.get("serverjoin.unknown"));
            return;
        }
        if (directory.enter(player.getUniqueId(), name) == ServerDirectory.Entry.CLOSED) {
            player.sendMessage(messages.get("serverjoin.closed"));
            return;
        }
        directory.send(player, server.get());
    }
}
