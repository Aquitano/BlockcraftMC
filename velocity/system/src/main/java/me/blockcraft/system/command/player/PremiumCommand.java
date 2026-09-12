package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class PremiumCommand {

    private final CommandSupport support;
    private final SystemConfig config;
    private final Messages messages;

    public PremiumCommand(CommandSupport support, SystemConfig config, Messages messages) {
        this.support = support;
        this.config = config;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("premium")
                .executes(support.asPlayer((player, context) ->
                        messages.getLines("premium", Placeholder.unparsed("shop", config.network().shop())).forEach(player::sendMessage))));
    }
}
