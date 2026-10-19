package me.blockcraft.quicksg.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.StatsRepository;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class StartCommand {

    private static final int DEBUG_TIMER_SECONDS = 950;

    private StartCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, Game game, Messages messages, StatsRepository stats) {
        return Commands.literal("start")
                .requires(source -> source.getSender() instanceof Player
                        && source.getSender().hasPermission("blockcraft.quicksg.start"))
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrThrow();
                    if (game.alivePlayers().size() < Game.MIN_TO_START) {
                        player.sendMessage(messages.get("start.not-enough-players"));
                    } else if (!game.isLobby()) {
                        player.sendMessage(messages.get("start.only-in-lobby"));
                    } else if (!game.shortenLobbyCountdown()) {
                        player.sendMessage(messages.get("start.already-started"));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("timer")
                        .requires(source -> source.getSender().hasPermission("blockcraft.quicksg.admin"))
                        .executes(context -> {
                            game.skipGameTimer(DEBUG_TIMER_SECONDS);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("wins")
                        .requires(source -> source.getSender().hasPermission("blockcraft.quicksg.admin"))
                        .executes(context -> {
                            Player player = context.getSource().getPlayerOrThrow();
                            Tasks.async(plugin, () -> stats.add(player.getUniqueId(), BlockcraftApi.get().realName(player), Stat.WINS, 1));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
