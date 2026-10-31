package me.blockcraft.system.command.player;

import com.velocitypowered.api.command.BrigadierCommand;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.network.Rejoins;

/** Back into the running round whose server still keeps the player's place. */
public final class RejoinCommand {

    private final CommandSupport support;
    private final Rejoins rejoins;

    public RejoinCommand(CommandSupport support, Rejoins rejoins) {
        this.support = support;
        this.rejoins = rejoins;
    }

    public BrigadierCommand create() {
        return new BrigadierCommand(BrigadierCommand.literalArgumentBuilder("rejoin")
                .executes(support.asPlayer((player, context) -> rejoins.rejoin(player))));
    }
}
