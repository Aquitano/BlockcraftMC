package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;

/** {@code /rain} and {@code /sun} for the player's world. */
public final class WeatherCommand {

    private WeatherCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> rain(Messages messages) {
        return create("rain", true, messages);
    }

    public static LiteralCommandNode<CommandSourceStack> sun(Messages messages) {
        return create("sun", false, messages);
    }

    private static LiteralCommandNode<CommandSourceStack> create(String name, boolean storm, Messages messages) {
        return Commands.literal(name)
                .requires(CorePermission.player(name))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.getWorld().setStorm(storm);
                    player.sendMessage(messages.get(name + ".done"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
