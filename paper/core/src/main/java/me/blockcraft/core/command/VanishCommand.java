package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import me.blockcraft.core.staff.VanishService;

public final class VanishCommand {

    private VanishCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(VanishService vanish, StaffAudit audit, Messages messages) {
        return Commands.literal("v")
                .requires(CorePermission.player("vanish"))
                .executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    boolean vanished = vanish.toggle(player);
                    audit.record(player, vanished ? "vanish.on" : "vanish.off");
                    player.sendMessage(messages.get(vanished ? "vanish.enabled" : "vanish.disabled"));
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }
}
