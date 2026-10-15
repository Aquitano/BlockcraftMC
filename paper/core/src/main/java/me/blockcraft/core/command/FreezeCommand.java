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
                            var staff = context.getSource().getPlayerOrThrow();
                            var target = Players.resolve(context, "player");
                            if (!TargetGuard.notSelf(staff, target, messages) || !TargetGuard.mayTarget(staff, target, messages)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            boolean frozen = freeze.toggle(target, staff);
                            audit.record(staff, frozen ? "freeze.on" : "freeze.off", target);
                            staff.sendMessage(messages.get(frozen ? "freeze.frozen" : "freeze.unfrozen",
                                    Placeholder.unparsed("player", target.getName())));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
