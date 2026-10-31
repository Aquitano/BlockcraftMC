package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.team.TeamBoard;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class TeamBoardCommand {

    private TeamBoardCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(TeamBoard board, Messages messages) {
        var root = Commands.literal("teamboard")
                .requires(source -> source.getSender().hasPermission(LobbyPermissions.SET_SPAWN));
        for (var action : new String[]{"check", "apply"}) {
            root.then(Commands.literal(action).executes(context -> {
                var result = board.refresh(action.equals("apply"));
                var sender = context.getSource().getSender();
                if (!result.problems().isEmpty()) {
                    sender.sendMessage(messages.get("teamboard.invalid",
                            Placeholder.unparsed("fields", String.join(", ", result.problems()))));
                    return 0;
                }
                sender.sendMessage(messages.get("teamboard." + (action.equals("apply") ? "applied" : "ready"),
                        Formatter.number("members", result.members()), Formatter.number("slots", result.slots())));
                return Command.SINGLE_SUCCESS;
            }));
        }
        return root.build();
    }
}
