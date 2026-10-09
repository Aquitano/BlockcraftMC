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

public final class FoodCommand {

    private FoodCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffAudit audit, Messages messages) {
        return Commands.literal("food")
                .requires(CorePermission.of("food"))
                .executes(context -> {
                    Players.self(context.getSource(), messages.get("food.usage")).ifPresent(player -> feed(player, player, audit, messages));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("players", ArgumentTypes.players())
                        .requires(CorePermission.of("food.others"))
                        .executes(context -> {
                            var sender = context.getSource().getSender();
                            TargetGuard.permitted(sender, Players.resolveAll(context, "players"), messages)
                                    .forEach(target -> feed(sender, target, audit, messages));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static void feed(CommandSender sender, Player target, StaffAudit audit, Messages messages) {
        target.setFoodLevel(20);
        target.sendMessage(messages.get("food.done"));
        if (!target.equals(sender)) {
            sender.sendMessage(messages.get("food.other", Placeholder.unparsed("player", target.getName())));
            audit.record(sender, "food", target);
        }
    }
}
