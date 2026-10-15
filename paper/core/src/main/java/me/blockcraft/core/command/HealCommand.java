package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class HealCommand {

    private HealCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffAudit audit, Messages messages) {
        return Commands.literal("heal")
                .requires(CorePermission.of("heal"))
                .executes(context -> {
                    Players.self(context.getSource(), messages.get("heal.usage")).ifPresent(player -> heal(player, player, audit, messages));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("players", ArgumentTypes.players())
                        .requires(CorePermission.of("heal.others"))
                        .executes(context -> {
                            var sender = context.getSource().getSender();
                            TargetGuard.permitted(sender, Players.resolveAll(context, "players"), messages)
                                    .forEach(target -> heal(sender, target, audit, messages));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static void heal(CommandSender sender, Player target, StaffAudit audit, Messages messages) {
        target.setFoodLevel(20);
        target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
        target.sendMessage(messages.get("heal.done"));
        if (!target.equals(sender)) {
            sender.sendMessage(messages.get("heal.other", Placeholder.unparsed("player", target.getName())));
            audit.record(sender, "heal", target);
        }
    }
}
