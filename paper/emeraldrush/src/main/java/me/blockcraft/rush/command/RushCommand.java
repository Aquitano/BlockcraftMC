package me.blockcraft.rush.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.Permissions;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.menu.RushMenus;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;

public final class RushCommand {

    private RushCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Game game, RushMenus menus, Messages messages) {
        return Commands.literal("rush")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission(Permissions.ADMIN))
                .then(Commands.literal("endsuch").executes(context -> {
                    game.skipSearch();
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("endshop").executes(context -> {
                    game.skipShopping();
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("shop").executes(context -> {
                    menus.openShop(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("emeralds")
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> {
                                            var target = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                                                    .resolve(context.getSource()).getFirst();
                                            int amount = IntegerArgumentType.getInteger(context, "amount");
                                            game.setEmeralds(target, amount);
                                            context.getSource().getSender().sendMessage(
                                                    messages.get("command.emeralds-set", Formatter.number("amount", amount)));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
    }
}
