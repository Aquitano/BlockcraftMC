package me.blockcraft.api.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

/** /start, the same in every game: the lobby countdown runs down to {@code countdown.start} seconds. */
public final class StartCommand {

    private StartCommand() {
    }

    /** A game may add its own debug subcommands before building it. */
    public static LiteralArgumentBuilder<CommandSourceStack> of(LobbyCountdown countdown) {
        return Commands.literal("start")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission(GamePermissions.START))
                .executes(context -> {
                    countdown.forceStart(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                });
    }
}
