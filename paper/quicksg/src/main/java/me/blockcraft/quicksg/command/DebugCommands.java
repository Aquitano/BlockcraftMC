package me.blockcraft.quicksg.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.quicksg.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** QuickSG's additions to /start for trying a round alone: {@code timer} jumps the round clock, {@code wins} counts a win. */
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

    public static LiteralArgumentBuilder<CommandSourceStack> wins(Plugin plugin, StatsRepository stats) {
        return Commands.literal("wins")
                .requires(source -> source.getSender().hasPermission(ADMIN))
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrThrow();
                    Tasks.async(plugin, () -> stats.add(player.getUniqueId(), BlockcraftApi.get().realName(player), Stat.WINS, 1));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
