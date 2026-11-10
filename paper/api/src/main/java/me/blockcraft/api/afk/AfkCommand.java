package me.blockcraft.api.afk;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;

public final class AfkCommand {

    static final String NAME = "afk";

    private AfkCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Afk afk) {
        return Commands.literal(NAME)
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    afk.toggle(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
