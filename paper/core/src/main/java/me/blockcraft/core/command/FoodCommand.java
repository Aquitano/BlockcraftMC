package me.blockcraft.core.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;

public final class FoodCommand {

    private FoodCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffAudit audit, Messages messages) {
        return new SelfOrOthers("food", messages)
                .executes(Commands.literal("food").requires(CorePermission.of("food")), (sender, target) -> {
                    target.setFoodLevel(20);
                    target.sendMessage(messages.pick("food.done"));
                    return SelfOrOthers.Outcome.told("food.other").audited(audit, "food");
                })
                .build();
    }
}
