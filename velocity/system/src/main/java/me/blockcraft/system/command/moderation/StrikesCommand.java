package me.blockcraft.system.command.moderation;

import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.StrikeRepository;
import me.blockcraft.system.SystemPermissions;
import me.blockcraft.system.command.CommandSupport;

/** Shows a team member their own strikes. */
public final class StrikesCommand {

    private final CommandSupport support;
    private final StrikeList strikeList;
    private final Messages messages;

    public StrikesCommand(CommandSupport support, StrikeRepository strikes, Messages messages) {
        this.support = support;
        this.strikeList = new StrikeList(strikes, messages);
        this.messages = messages;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("strikes")
                .executes(support.asPlayer((player, context) -> {
                    if (player.hasPermission(SystemPermissions.STRIKES)) {
                        strikeList.send(player, player.getUsername(), player.getUniqueId());
                    } else {
                        player.sendMessage(messages.get("strikes.not-collected"));
                    }
                })));
    }
}
