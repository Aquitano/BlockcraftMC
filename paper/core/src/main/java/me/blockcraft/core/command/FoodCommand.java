package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;

public final class FoodCommand {

    private FoodCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("food")
                .requires(CorePermission.player("food"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.setFoodLevel(20);
                    player.sendMessage(messages.get("food.done"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
