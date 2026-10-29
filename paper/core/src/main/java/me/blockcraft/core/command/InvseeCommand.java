package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import me.blockcraft.core.staff.InvseeGuard;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public final class InvseeCommand {

    private InvseeCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffAudit audit, Messages messages) {
        return Commands.literal("invsee")
                .requires(CorePermission.player("invsee"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("invsee.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            open(context.getSource().getPlayerOrThrow(), Players.resolve(context, "player"), audit, messages);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    public static void open(Player viewer, Player target, StaffAudit audit, Messages messages) {
        if (!TargetGuard.notSelf(viewer, target, messages) || !TargetGuard.mayTarget(viewer, target, messages)) {
            return;
        }
        viewer.closeInventory();
        var name = Placeholder.unparsed("player", target.getName());
        var view = viewer.openInventory(target.getInventory());
        if (view == null) {
            viewer.sendMessage(messages.get("invsee.failed", name));
            return;
        }
        viewer.sendMessage(messages.get("invsee.opened", name));
        boolean readOnly = InvseeGuard.isReadOnly(viewer, view);
        viewer.sendMessage(messages.get(readOnly ? "invsee.read-only" : "invsee.editable"));
        audit.record(viewer, readOnly ? "invsee.read" : "invsee.edit", target);
    }
}
