package me.blockcraft.rush.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GamePhase;
import me.blockcraft.rush.menu.RushMenus;
import org.bukkit.entity.Player;

public final class ChestCommand {

    private ChestCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Game game, RushMenus menus, Messages messages) {
        return Commands.literal("chest")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    if (game.phase() != GamePhase.SHOPPING) {
                        player.sendMessage(messages.get("command.chest-wrong-phase"));
                    } else if (game.isAlive(player)) {
                        menus.openShop(player);
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
