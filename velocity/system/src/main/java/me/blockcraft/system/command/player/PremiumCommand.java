package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.command.CommandSupport;

public final class PremiumCommand {

    private final CommandSupport support;
    private final Messages messages;

    public PremiumCommand(CommandSupport support, Messages messages) {
        this.support = support;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("premium")
                .executes(support.asPlayer((player, context) ->
                        messages.getLines("premium").forEach(player::sendMessage))));
    }
}
