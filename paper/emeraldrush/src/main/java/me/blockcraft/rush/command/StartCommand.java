package me.blockcraft.rush.command;

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
import me.blockcraft.rush.Permissions;
import me.blockcraft.rush.game.Game;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class StartCommand {

    private static final int BONUS_COINS = 200;

    private StartCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, Game game, CoinsRepository coins, Messages messages) {
        return Commands.literal("start")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission(Permissions.START))
                .executes(context -> {
                    game.forceStart(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("coins")
                        .requires(source -> source.getSender().hasPermission(Permissions.ADMIN))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(context -> {
                                    var sender = context.getSource().getSender();
                                    var target = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                                            .resolve(context.getSource()).getFirst();
                                    Tasks.async(plugin, () -> coins.add(target.getUniqueId(), BlockcraftApi.get().realName(target), BONUS_COINS));
                                    sender.sendMessage(messages.get("command.coins-given", Formatter.number("amount", BONUS_COINS)));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
