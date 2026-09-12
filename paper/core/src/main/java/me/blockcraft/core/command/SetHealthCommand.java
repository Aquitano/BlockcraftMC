package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.attribute.Attribute;

public final class SetHealthCommand {

    private SetHealthCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("sethealth")
                .requires(CorePermission.player("sethealth"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("sethealth.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("player", ArgumentTypes.player())
                        .executes(context -> {
                            context.getSource().getSender().sendMessage(messages.get("sethealth.usage"));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("health", IntegerArgumentType.integer(1, 1024))
                                .executes(context -> {
                                    var target = Players.resolve(context, "player");
                                    int health = IntegerArgumentType.getInteger(context, "health");
                                    var maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
                                    if (maxHealth != null) {
                                        maxHealth.setBaseValue(health);
                                    }
                                    target.setHealth(health);
                                    context.getSource().getSender().sendMessage(messages.get("sethealth.done",
                                            Placeholder.unparsed("player", target.getName()),
                                            Formatter.number("health", health)));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
