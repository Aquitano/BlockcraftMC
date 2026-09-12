package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

public final class OnlineCommand {

    private final CommandSupport support;
    private final ProxyServer proxy;
    private final Messages messages;

    public OnlineCommand(CommandSupport support, ProxyServer proxy, Messages messages) {
        this.support = support;
        this.proxy = proxy;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("online")
                .executes(support.asPlayer((player, context) ->
                        player.sendMessage(messages.get("online", Formatter.number("count", proxy.getPlayerCount()))))));
    }
}
