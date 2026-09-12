package me.blockcraft.api.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public final class CoinsCommand {

    private CoinsCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(CoinsRepository coins, Messages messages) {
        return Commands.literal("coins")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    Player player = context.getSource().getPlayerOrThrow();
                    player.sendMessage(messages.get("coins.self", Formatter.number("coins", coins.get(player.getUniqueId()))));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .requires(source -> source.getSender().hasPermission("blockcraft.coins.others"))
                        .executes(context -> {
                            Player target = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                                    .resolve(context.getSource()).getFirst();
                            context.getSource().getSender().sendMessage(messages.get("coins.other",
                                    Placeholder.unparsed("player", target.getName()),
                                    Formatter.number("coins", coins.get(target.getUniqueId()))));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
