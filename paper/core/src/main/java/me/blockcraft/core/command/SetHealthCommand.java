package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;

/** Health above the maximum comes from a transient modifier, so it ends with the session instead of lasting in player data. */
public final class SetHealthCommand {

    private SetHealthCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("sethealth")
                .requires(CorePermission.of("sethealth"))
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
                                    var sender = context.getSource().getSender();
                                    var target = Players.resolve(context, "player");
                                    if (!TargetGuard.mayTarget(sender, target, messages)) {
                                        return Command.SINGLE_SUCCESS;
                                    }
                                    int health = IntegerArgumentType.getInteger(context, "health");
                                    var maxHealth = target.getAttribute(Attribute.MAX_HEALTH);
                                    if (maxHealth != null) {
                                        maxHealth.removeModifier(PlayerReset.EXTRA_HEALTH);
                                        double extra = health - maxHealth.getValue();
                                        if (extra > 0) {
                                            maxHealth.addTransientModifier(
                                                    new AttributeModifier(PlayerReset.EXTRA_HEALTH, extra, AttributeModifier.Operation.ADD_NUMBER));
                                        }
                                    }
                                    target.setHealth(health);
                                    sender.sendMessage(messages.get("sethealth.done",
                                            Placeholder.unparsed("player", target.getName()),
                                            Formatter.number("health", health)));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .build();
    }
}
