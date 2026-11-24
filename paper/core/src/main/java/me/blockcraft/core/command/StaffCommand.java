package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.core.staff.StaffMode;

public final class StaffCommand {

    private StaffCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffMode staffMode) {
        return Commands.literal("staff")
                .requires(CorePermission.player("staff"))
                .executes(context -> {
                    staffMode.toggle(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
