package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public final class InvseeCommand {

    private InvseeCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("invsee")
                .requires(CorePermission.player("invsee"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("invsee.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            open(context.getSource().getPlayerOrThrow(), Players.resolve(context, "player"), messages);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    public static void open(Player viewer, Player target, Messages messages) {
        viewer.closeInventory();
        viewer.openInventory(target.getInventory());
        viewer.sendMessage(messages.get("invsee.opened", Placeholder.unparsed("player", target.getName())));
    }
}
