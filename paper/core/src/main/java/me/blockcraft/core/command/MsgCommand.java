package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Sound;

public final class MsgCommand {

    private MsgCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("msg")
                .requires(CorePermission.player("msg"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("msg.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            context.getSource().getSender().sendMessage(messages.get("msg.usage"));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> {
                                    var sender = context.getSource().getPlayerOrThrow();
                                    var target = Players.resolve(context, "player");
                                    if (target.getUniqueId().equals(sender.getUniqueId())) {
                                        sender.sendMessage(messages.get("msg.self"));
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    var message = LegacyComponentSerializer.legacyAmpersand()
                                            .deserialize(StringArgumentType.getString(context, "message"));
                                    var resolvers = TagResolver.resolver(
                                            Placeholder.component("sender", sender.playerListName()),
                                            Placeholder.component("target", target.playerListName()),
                                            Placeholder.component("message", message));
                                    target.playSound(target.getLocation(), Sound.UI_BUTTON_CLICK, 4.0f, 4.0f);
                                    target.sendMessage(messages.get("msg.to-target", resolvers));
                                    sender.sendMessage(messages.get("msg.to-sender", resolvers));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
