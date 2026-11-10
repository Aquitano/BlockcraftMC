package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.item.Items;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class SkullCommand {

    private SkullCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("skull")
                .requires(CorePermission.player("skull"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("skull.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            String name = StringArgumentType.getString(context, "name");
                            player.getInventory().addItem(Items.head(null, name).build());
                            player.sendMessage(messages.get("skull.received", Placeholder.unparsed("player", name)));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
