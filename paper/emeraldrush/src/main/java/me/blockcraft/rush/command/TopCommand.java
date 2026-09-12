package me.blockcraft.rush.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.rush.menu.RushMenus;
import me.blockcraft.rush.storage.StatCategory;
import org.bukkit.entity.Player;

public final class TopCommand {

    private TopCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(RushMenus menus) {
        var top = Commands.literal("top").requires(source -> source.getSender() instanceof Player);
        for (var category : StatCategory.values()) {
            top.then(Commands.literal(category.key()).executes(context -> {
                menus.showTop(context.getSource().getPlayerOrThrow(), category);
                return Command.SINGLE_SUCCESS;
            }));
        }
        return top.build();
    }
}
