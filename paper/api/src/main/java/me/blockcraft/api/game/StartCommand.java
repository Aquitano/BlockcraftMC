package me.blockcraft.api.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

/** /start, the same in every game: the lobby countdown runs down to {@code countdown.start} seconds. */
public final class StartCommand {

    private StartCommand() {
    }

    /** A game may add its own debug subcommands before building it. */
    public static LiteralArgumentBuilder<CommandSourceStack> of(LobbyCountdown countdown) {
        return of(() -> countdown);
    }

    /** As {@link #of(LobbyCountdown)}, for the countdown of whichever round a {@link RoundHost} plays now. */
    public static LiteralArgumentBuilder<CommandSourceStack> of(RoundHost<?> host) {
        return of(() -> host.current().lobbyCountdown());
    }

    /** As {@link #of(LobbyCountdown)}, for a countdown that changes over the server's life. */
    public static LiteralArgumentBuilder<CommandSourceStack> of(Supplier<LobbyCountdown> countdown) {
        return Commands.literal("start")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission(GamePermissions.START))
                .executes(context -> {
                    countdown.get().forceStart(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                });
    }
}
