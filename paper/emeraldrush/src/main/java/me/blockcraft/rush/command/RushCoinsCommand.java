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
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;

/** Sets a player's emeralds for the running round (not their global coins). */
public final class RushCoinsCommand {

    private RushCoinsCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Game game, Messages messages) {
        return Commands.literal("rushcoins")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission(Permissions.START))
                .then(Commands.literal("set")
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
