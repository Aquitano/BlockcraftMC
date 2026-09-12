package me.blockcraft.system.command.staff;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.command.CommandSupport;

/** Target of the /joinme click action: connects the player to the named server. */
public final class ServerJoinCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final Messages messages;

    public ServerJoinCommand(CommandSupport support, ProxyServer proxy, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("serverjoin")
                .then(BrigadierCommand.requiredArgumentBuilder("server", StringArgumentType.word())
                        .executes(support.asPlayer((player, context) ->
                                proxy.getServer(StringArgumentType.getString(context, "server")).ifPresentOrElse(
                                        server -> player.createConnectionRequest(server).fireAndForget(),
                                        () -> player.sendMessage(messages.get("serverjoin.unknown")))))));
    }
}
