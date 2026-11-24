package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.lobby.activity.Parkour;
import org.bukkit.entity.Player;

public final class ParkourCommand {

    private ParkourCommand() { }

    public static LiteralCommandNode<CommandSourceStack> create(Parkour parkour) {
        return Commands.literal("parkour")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    parkour.retry(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("retry").executes(context -> {
                    parkour.retry(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                }))
                .build();
    }
}
