package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.predicate.ItemStackPredicate;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.OptionalInt;
import java.util.function.Predicate;

/** Vanilla's {@code /clear [targets] [item] [maxCount]}, with the rank guard and German feedback; a maxCount of 0 only counts. */
public final class ClearCommand {

    private static final Predicate<ItemStack> EVERYTHING = item -> true;

    private ClearCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffAudit audit, Messages messages) {
        return Commands.literal("clear")
                .requires(CorePermission.of("clear"))
                .executes(context -> {
                    Players.self(context.getSource(), messages.get("clear.usage"))
                            .ifPresent(player -> clear(player, player, EVERYTHING, OptionalInt.empty(), audit, messages));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("players", ArgumentTypes.players())
                        .executes(context -> run(context, EVERYTHING, OptionalInt.empty(), audit, messages))
                        .then(Commands.argument("item", ArgumentTypes.itemPredicate())
                                .executes(context -> run(context, context.getArgument("item", ItemStackPredicate.class),
                                        OptionalInt.empty(), audit, messages))
                                .then(Commands.argument("maxCount", IntegerArgumentType.integer(0))
                                        .executes(context -> run(context, context.getArgument("item", ItemStackPredicate.class),
                                                OptionalInt.of(IntegerArgumentType.getInteger(context, "maxCount")), audit, messages)))))
                .build();
    }

    private static int run(CommandContext<CommandSourceStack> context, Predicate<ItemStack> filter, OptionalInt limit, StaffAudit audit,
                           Messages messages) throws CommandSyntaxException {
        var sender = context.getSource().getSender();
        TargetGuard.permitted(sender, Players.resolveAll(context, "players"), messages)
                .forEach(target -> clear(sender, target, filter, limit, audit, messages));
        return Command.SINGLE_SUCCESS;
    }

    private static void clear(CommandSender sender, Player target, Predicate<ItemStack> filter, OptionalInt limit, StaffAudit audit,
                              Messages messages) {
        int count = remove(target.getInventory(), filter, limit);
        var player = Placeholder.unparsed("player", target.getName());
        if (limit.equals(OptionalInt.of(0))) {
            sender.sendMessage(messages.get("clear.found", player, Formatter.number("count", count)));
            return;
        }
        sender.sendMessage(messages.get("clear.done", player, Formatter.number("count", count)));
        if (!target.equals(sender)) {
            audit.record(sender, "clear", target, String.valueOf(count));
        }
    }

    /** Takes up to {@code limit} matching items, all of them without one, and returns how many matched. */
    private static int remove(PlayerInventory inventory, Predicate<ItemStack> filter, OptionalInt limit) {
        int found = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var item = inventory.getItem(slot);
            if (item == null || item.isEmpty() || !filter.test(item)) {
                continue;
            }
            if (limit.equals(OptionalInt.of(0))) {
                found += item.getAmount();
                continue;
            }
            int take = limit.isPresent() ? Math.min(item.getAmount(), limit.getAsInt() - found) : item.getAmount();
            item.setAmount(item.getAmount() - take);
            inventory.setItem(slot, item.isEmpty() ? null : item);
            found += take;
            if (limit.isPresent() && found >= limit.getAsInt()) {
                break;
            }
        }
        return found;
    }
}
