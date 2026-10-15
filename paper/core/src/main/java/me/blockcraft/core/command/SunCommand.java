package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;

public final class SunCommand {

    private SunCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("sun")
                .requires(CorePermission.player("sun"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.getWorld().setStorm(false);
                    player.sendMessage(messages.get("sun.done"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
