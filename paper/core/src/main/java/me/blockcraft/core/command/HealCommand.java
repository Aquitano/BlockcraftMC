package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import org.bukkit.attribute.Attribute;

public final class HealCommand {

    private HealCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("heal")
                .requires(CorePermission.player("heal"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    player.setFoodLevel(20);
                    player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                    player.sendMessage(messages.get("heal.done"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
