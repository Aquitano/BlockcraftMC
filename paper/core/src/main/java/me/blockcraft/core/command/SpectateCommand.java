package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.common.message.Messages;
import me.blockcraft.core.staff.StaffMode;

public final class SpectateCommand {

    private SpectateCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffMode staffMode, Messages messages) {
        return Commands.literal("spec")
                .requires(CorePermission.player("staff"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("staff.spec-usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("stop")
                        .executes(context -> {
                            staffMode.stopSpectating(context.getSource().getPlayerOrThrow());
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            staffMode.spectate(context.getSource().getPlayerOrThrow(), Players.resolve(context, "player"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
