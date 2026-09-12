package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

public final class PingCommand {

    private final CommandSupport support;
    private final Messages messages;

    public PingCommand(CommandSupport support, Messages messages) {
        this.support = support;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("ping")
                .executes(support.asPlayer((player, context) ->
                        player.sendMessage(messages.get("ping", Formatter.number("ping", player.getPing()))))));
    }
}
