package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Back to where the last teleport by a core staff command picked the player up; a second {@code /back} undoes it. */
public final class BackCommand {

    private BackCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(BackLocations back, StaffAudit audit, Messages messages) {
        return Commands.literal("back")
                .requires(CorePermission.of("back"))
                .executes(context -> {
                    Players.self(context.getSource(), messages.get("back.usage")).ifPresent(player -> send(player, player, back, audit, messages));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .requires(CorePermission.of("back.others"))
                        .executes(context -> {
                            var sender = context.getSource().getSender();
                            var target = Players.resolve(context, "player");
                            if (TargetGuard.mayTarget(sender, target, messages)) {
                                send(sender, target, back, audit, messages);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static void send(CommandSender sender, Player target, BackLocations back, StaffAudit audit, Messages messages) {
        var player = Placeholder.unparsed("player", target.getName());
        var location = back.of(target);
        if (location.isEmpty()) {
            sender.sendMessage(messages.get(target.equals(sender) ? "back.none" : "back.none-other", player));
            return;
        }
        if (!back.teleport(target, location.get())) {
            return;
        }
        target.sendMessage(messages.get("back.done"));
        if (target.equals(sender)) {
            audit.record(sender, "back");
        } else {
            sender.sendMessage(messages.get("back.other", player));
            audit.record(sender, "back", target);
        }
    }
}
