package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.LobbyPermissions;
import me.blockcraft.lobby.activity.SantaSleigh;
import org.bukkit.entity.Player;

/** Lets staff send Santa's sleigh over themselves on any day, to show it off or to check it before Christmas. */
public final class SleighCommand {

    private SleighCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(SantaSleigh sleigh, Messages messages) {
        return Commands.literal("schlitten")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission(LobbyPermissions.SET_SPAWN))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    if (!sleigh.launch(player.getLocation())) {
                        player.sendMessage(messages.get("seasonal.sleigh-busy"));
                        return 0;
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
