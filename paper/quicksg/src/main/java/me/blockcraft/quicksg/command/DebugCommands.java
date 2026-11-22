package me.blockcraft.quicksg.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.quicksg.game.Game;

/** QuickSG's addition to /start for trying a round alone: {@code timer} jumps the round clock. */
public final class DebugCommands {

    private static final String ADMIN = "blockcraft.quicksg.admin";
    private static final int DEBUG_TIMER_SECONDS = 950;

    private DebugCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> timer(Game game) {
        return Commands.literal("timer")
                .requires(source -> source.getSender().hasPermission(ADMIN))
                .executes(context -> {
                    game.skipGameTimer(DEBUG_TIMER_SECONDS);
                    return Command.SINGLE_SUCCESS;
                });
    }
}
