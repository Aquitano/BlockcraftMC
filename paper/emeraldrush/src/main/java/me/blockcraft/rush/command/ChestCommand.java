package me.blockcraft.rush.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GamePhase;
import org.bukkit.entity.Player;

import java.util.function.Supplier;

public final class ChestCommand {

    private ChestCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Supplier<Game> game, Messages messages) {
        return Commands.literal("chest")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    var current = game.get();
                    if (current.phase() != GamePhase.SHOPPING) {
                        player.sendMessage(messages.get("command.chest-wrong-phase"));
                    } else if (current.isAlive(player)) {
                        current.menus().openShop(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
