package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class AchievementsCommand {

    private AchievementsCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create() {
        return Commands.literal("erfolge")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    BlockcraftApi.get().achievements().open(context.getSource().getPlayerOrThrow(), Optional.empty());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
