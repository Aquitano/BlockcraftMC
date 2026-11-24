package me.blockcraft.rush.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.game.Game;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

public final class RushCommand {

    private static final String ADMIN = "blockcraft.rush.admin";

    private RushCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Supplier<Game> game, Messages messages) {
        return Commands.literal("rush")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission(ADMIN))
                .then(Commands.literal("endsuch").executes(context -> {
                    game.get().skipSearch();
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("endshop").executes(context -> {
                    game.get().skipShopping();
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("shop").executes(context -> {
                    game.get().menus().openShop(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("emeralds")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            var target = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                                                    .resolve(context.getSource()).getFirst();
                                            int amount = IntegerArgumentType.getInteger(context, "amount");
                                            game.get().setEmeralds(target, amount);
                                            context.getSource().getSender().sendMessage(
                                                    messages.get("command.emeralds-set", Formatter.number("amount", amount)));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
    }
}
