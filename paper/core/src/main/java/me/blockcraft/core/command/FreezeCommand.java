package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import me.blockcraft.core.staff.FreezeService;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

public final class FreezeCommand {

    private FreezeCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(FreezeService freeze, StaffAudit audit, Messages messages) {
        return Commands.literal("freeze")
                .requires(CorePermission.player("freeze"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("freeze.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            toggle(context.getSource().getPlayerOrThrow(), Players.resolve(context, "player"), freeze, audit, messages);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    public static void toggle(Player staff, Player target, FreezeService freeze, StaffAudit audit, Messages messages) {
        if (!TargetGuard.notSelf(staff, target, messages) || !TargetGuard.mayTarget(staff, target, messages)) {
            return;
        }
        boolean frozen = freeze.toggle(target, staff);
        audit.record(staff, frozen ? "freeze.on" : "freeze.off", target);
        staff.sendMessage(messages.get(frozen ? "freeze.frozen" : "freeze.unfrozen",
                Placeholder.unparsed("player", target.getName())));
    }
}
