package me.blockcraft.signs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.signs.sign.StatusSign;
import me.blockcraft.signs.sign.StatusSigns;
import org.bukkit.Tag;
import org.bukkit.entity.Player;

public final class CreateSignCommand {

    private static final int TARGET_RANGE = 10;

    private CreateSignCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StatusSigns signs, Messages messages) {
        return Commands.literal("createsign")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("blockcraft.statussigns.create"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("createsign.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("ip", StringArgumentType.word())
                        .then(Commands.argument("port", IntegerArgumentType.integer(1, 65535))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(context -> {
                                            var player = context.getSource().getPlayerOrThrow();
                                            var target = player.getTargetBlockExact(TARGET_RANGE);
                                            if (target == null || !Tag.ALL_SIGNS.isTagged(target.getType())) {
                                                player.sendMessage(messages.get("createsign.not-a-sign"));
                                                return Command.SINGLE_SUCCESS;
                                            }
                                            signs.add(new StatusSign(
                                                    target.getLocation(),
                                                    StringArgumentType.getString(context, "name"),
                                                    StringArgumentType.getString(context, "ip"),
                                                    IntegerArgumentType.getInteger(context, "port")));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .build();
    }
}
