package me.blockcraft.core.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import org.bukkit.attribute.Attribute;

public final class HealCommand {

    private HealCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffAudit audit, Messages messages) {
        return new SelfOrOthers("heal", messages)
                .executes(Commands.literal("heal").requires(CorePermission.of("heal")), (sender, target) -> {
                    target.setFoodLevel(20);
                    target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
                    target.sendMessage(messages.get("heal.done"));
                    return SelfOrOthers.Outcome.told("heal.other").audited(audit, "heal");
                })
                .build();
    }
}
