package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class TpHereCommand {

    private TpHereCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(BackLocations back, StaffAudit audit, Messages messages) {
        return Commands.literal("tphere")
                .requires(CorePermission.player("tphere"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("tphere.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("players", ArgumentTypes.players())
                        .executes(context -> {
                            var staff = context.getSource().getPlayerOrThrow();
                            var permitted = TargetGuard.permitted(staff, Players.resolveAll(context, "players"), messages).stream()
                                    .filter(target -> !target.equals(staff)).toList();
                            var moved = permitted.stream().filter(target -> back.teleport(target, staff.getLocation())).toList();
                            if (moved.size() < permitted.size()) {
                                staff.sendMessage(messages.get("teleport.failed-many", Formatter.number("count", permitted.size() - moved.size())));
                            }
                            moved.forEach(target -> audit.record(staff, "tphere", target));
                            if (moved.size() == 1) {
                                staff.sendMessage(messages.get("tphere.done", Placeholder.unparsed("player", moved.getFirst().getName())));
                            } else if (!moved.isEmpty()) {
                                staff.sendMessage(messages.get("tphere.many", Formatter.number("count", moved.size())));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
