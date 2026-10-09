package me.blockcraft.core.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

final class Players {

    private Players() {
    }

    static Player resolve(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return context.getArgument(argument, PlayerSelectorArgumentResolver.class).resolve(context.getSource()).getFirst();
    }

    static List<Player> resolveAll(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return context.getArgument(argument, PlayerSelectorArgumentResolver.class).resolve(context.getSource());
    }

    static List<Entity> entities(CommandContext<CommandSourceStack> context, String argument) throws CommandSyntaxException {
        return context.getArgument(argument, EntitySelectorArgumentResolver.class).resolve(context.getSource());
    }

    /** The player running the command, or empty after telling a console which player the command needs. */
    static Optional<Player> self(CommandSourceStack source, Component usage) {
        if (source.getExecutor() instanceof Player player) {
            return Optional.of(player);
        }
        source.getSender().sendMessage(usage);
        return Optional.empty();
    }
}
