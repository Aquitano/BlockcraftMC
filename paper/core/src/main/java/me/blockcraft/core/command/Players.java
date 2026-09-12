package me.blockcraft.core.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.entity.Player;

final class Players {

    private Players() {
    }

    static Player resolve(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return context.getArgument(argument, PlayerSelectorArgumentResolver.class).resolve(context.getSource()).getFirst();
    }
}
