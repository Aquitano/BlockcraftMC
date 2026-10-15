package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

/** {@code /fly [players] [on|off]}: without on or off every target's flight flips. */
public final class FlyCommand {

    private static final Predicate<Player> TOGGLE = player -> !player.getAllowFlight();
    private static final Predicate<Player> ON = player -> true;
    private static final Predicate<Player> OFF = player -> false;

    private FlyCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffAudit audit, Messages messages) {
        return Commands.literal("fly")
                .requires(CorePermission.of("fly"))
                .executes(context -> self(context, TOGGLE, audit, messages))
                .then(Commands.literal("on").executes(context -> self(context, ON, audit, messages)))
                .then(Commands.literal("off").executes(context -> self(context, OFF, audit, messages)))
                .then(Commands.argument("players", ArgumentTypes.players())
                        .requires(CorePermission.of("fly.others"))
                        .executes(context -> others(context, TOGGLE, audit, messages))
                        .then(Commands.literal("on").executes(context -> others(context, ON, audit, messages)))
                        .then(Commands.literal("off").executes(context -> others(context, OFF, audit, messages))))
                .build();
    }

    private static int self(CommandContext<CommandSourceStack> context, Predicate<Player> flight, StaffAudit audit, Messages messages) {
        Players.self(context.getSource(), messages.get("fly.usage")).ifPresent(player -> apply(player, player, flight, audit, messages));
        return Command.SINGLE_SUCCESS;
    }

    private static int others(CommandContext<CommandSourceStack> context, Predicate<Player> flight, StaffAudit audit, Messages messages)
            throws CommandSyntaxException {
        var sender = context.getSource().getSender();
        TargetGuard.permitted(sender, Players.resolveAll(context, "players"), messages)
                .forEach(target -> apply(sender, target, flight, audit, messages));
        return Command.SINGLE_SUCCESS;
    }

    private static void apply(CommandSender sender, Player target, Predicate<Player> flight, StaffAudit audit, Messages messages) {
        boolean flying = flight.test(target);
        target.setAllowFlight(flying);
        target.setFlying(flying);
        target.sendMessage(messages.get(flying ? "fly.enabled" : "fly.disabled"));
        if (!target.equals(sender)) {
            sender.sendMessage(messages.get(flying ? "fly.other-enabled" : "fly.other-disabled",
                    Placeholder.unparsed("player", target.getName())));
            audit.record(sender, flying ? "fly.on" : "fly.off", target);
        }
    }
}
