package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

public final class OnlineCommand {

    private final CommandSupport support;
    private final PlayerVisibility visibility;
    private final Messages messages;

    public OnlineCommand(CommandSupport support, PlayerVisibility visibility, Messages messages) {
        this.support = support;
        this.visibility = visibility;
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("online")
                .executes(support.asPlayer((player, context) ->
                        player.sendMessage(messages.get("online", Formatter.number("count", visibility.countFor(player)))))));
    }
}
