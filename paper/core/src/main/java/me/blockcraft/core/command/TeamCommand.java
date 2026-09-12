package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;

public final class TeamCommand {

    private static final String ADMIN_PERMISSION = "blockcraft.core.team.admin";

    private TeamCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("team")
                .requires(CorePermission.player("team"))
                .then(Commands.literal("help")
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            messages.getLines("team.help").forEach(player::sendMessage);
                            if (player.hasPermission(ADMIN_PERMISSION)) {
                                messages.getLines("team.admin-hint").forEach(player::sendMessage);
                            }
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("admin")
                                .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                                .executes(context -> {
                                    var player = context.getSource().getPlayerOrThrow();
                                    messages.getLines("team.admin").forEach(player::sendMessage);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
