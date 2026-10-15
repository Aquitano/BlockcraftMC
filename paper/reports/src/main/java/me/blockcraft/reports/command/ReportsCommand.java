package me.blockcraft.reports.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.reports.ReportsPlugin;
import me.blockcraft.reports.menu.ReportMenus;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class ReportsCommand {

    private ReportsCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(ReportMenus menus, Messages messages) {
        return Commands.literal("reports")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> open(context.getSource().getPlayerOrThrow(), Optional.empty(), menus, messages))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> open(context.getSource().getPlayerOrThrow(),
                                Optional.of(StringArgumentType.getString(context, "player")), menus, messages)))
                .build();
    }

    private static int open(Player player, Optional<String> reported, ReportMenus menus, Messages messages) {
        if (player.hasPermission(ReportsPlugin.MENU_PERMISSION)) {
            menus.openList(player, reported, 1);
        } else {
            player.sendMessage(messages.get("no-permission"));
            player.sendMessage(messages.get("no-permission-hint"));
        }
        return Command.SINGLE_SUCCESS;
    }
}
