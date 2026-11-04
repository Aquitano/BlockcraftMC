package me.blockcraft.signs.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import me.blockcraft.signs.sign.StatusSign;
import me.blockcraft.signs.sign.StatusSigns;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
                .then(Commands.argument("group", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            signs.knownGroups().stream().filter(group -> group.startsWith(builder.getRemaining())).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            var target = player.getTargetBlockExact(TARGET_RANGE);
                            if (target == null || !StatusSign.isSignType(target.getType())) {
                                player.sendMessage(messages.get("createsign.not-a-sign"));
                                return Command.SINGLE_SUCCESS;
                            }
                            String group = StringArgumentType.getString(context, "group");
                            signs.add(new StatusSign(target.getLocation(), group));
                            player.sendMessage(messages.get("createsign.created", Placeholder.unparsed("name", group)));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
