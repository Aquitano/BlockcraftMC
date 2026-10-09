package me.blockcraft.quicksg.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.coins.CoinsRepository;
import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import me.blockcraft.api.game.Stat;
import me.blockcraft.api.game.StatsRepository;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class StartCommand {

    private static final int MIN_PLAYERS_TO_FORCE = 2;
    private static final int DEBUG_COINS = 200;
    private static final int DEBUG_TIMER_SECONDS = 950;

    private StartCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, Game game, Messages messages,
                                                                CoinsRepository coins, StatsRepository stats) {
        return Commands.literal("start")
                .requires(source -> source.getSender() instanceof Player
                        && source.getSender().hasPermission("blockcraft.quicksg.start"))
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrThrow();
                    if (Bukkit.getOnlinePlayers().size() < MIN_PLAYERS_TO_FORCE) {
                        player.sendMessage(messages.get("start.not-enough-players"));
                    } else if (!game.isLobby()) {
                        player.sendMessage(messages.get("start.only-in-lobby"));
                    } else if (!game.shortenLobbyCountdown()) {
                        player.sendMessage(messages.get("start.already-started"));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("coins")
                        .requires(source -> source.getSender().hasPermission("blockcraft.quicksg.admin"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(context -> {
                                    Player target = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                                            .resolve(context.getSource()).getFirst();
                                    Tasks.async(plugin, () -> coins.add(target.getUniqueId(), BlockcraftApi.get().realName(target), DEBUG_COINS));
                                    context.getSource().getSender().sendMessage(
                                            messages.get("start.coins-added", Formatter.number("coins", DEBUG_COINS)));
                                    return Command.SINGLE_SUCCESS;
                                })))
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
