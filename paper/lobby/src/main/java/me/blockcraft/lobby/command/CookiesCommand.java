package me.blockcraft.lobby.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.lobby.menu.Menus;
import org.bukkit.entity.Player;

public final class CookiesCommand {

    private CookiesCommand() { }

    public static LiteralCommandNode<CommandSourceStack> create(Menus menus) {
        return Commands.literal("cookies")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    menus.openCookies(context.getSource().getPlayerOrThrow());
                    return Command.SINGLE_SUCCESS;
                }).build();
    }
}
