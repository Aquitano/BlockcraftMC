package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.ranked.RankedQueue;

/** /ranked waits for a rated Duell opponent of similar Elo, /ranked leave stops waiting. */
public final class RankedCommand {

    private final CommandSupport support;
    private final RankedQueue queue;

    public RankedCommand(CommandSupport support, RankedQueue queue) {
        this.support = support;
        this.queue = queue;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("ranked")
                .executes(support.asPlayer((player, context) -> queue.join(player)))
                .then(BrigadierCommand.literalArgumentBuilder("leave")
                        .executes(support.asPlayer((player, context) -> queue.leave(player)))));
    }
}
