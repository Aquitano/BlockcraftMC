package me.blockcraft.api.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.blockcraft.api.coins.CoinsRepository;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CoinsCommand {

    private static final String OTHERS_PERMISSION = "blockcraft.coins.others";
    private static final String ADMIN_PERMISSION = "blockcraft.coins.admin";
    private static final int TOP_SIZE = 10;

    private enum Change { GIVE, TAKE, SET }

    private record Outcome(CoinsRepository.Account account, boolean applied, int coins) {
    }

    private CoinsCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Plugin plugin, CoinsRepository coins, Messages messages) {
        return Commands.literal("coins")
                .requires(source -> source.getSender() instanceof Player || source.getSender().hasPermission(ADMIN_PERMISSION))
                .executes(context -> {
                    var sender = context.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(messages.get("coins.usage"));
                        return Command.SINGLE_SUCCESS;
                    }
                    var uuid = player.getUniqueId();
                    Tasks.async(plugin, () -> coins.get(uuid),
                            balance -> player.sendMessage(messages.get("coins.self", Formatter.number("coins", balance))),
                            () -> failed(player, messages));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("top")
                        .executes(context -> {
                            var sender = context.getSource().getSender();
                            Tasks.async(plugin, () -> coins.top(TOP_SIZE), top -> sendTop(sender, messages, top),
                                    () -> failed(sender, messages));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(change(plugin, coins, messages, "give", Change.GIVE, 1))
                .then(change(plugin, coins, messages, "take", Change.TAKE, 1))
                .then(change(plugin, coins, messages, "set", Change.SET, 0))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .requires(source -> source.getSender().hasPermission(OTHERS_PERMISSION))
                        .executes(context -> {
                            var sender = context.getSource().getSender();
                            Player target = context.getArgument("player", PlayerSelectorArgumentResolver.class)
                                    .resolve(context.getSource()).getFirst();
                            var name = Placeholder.unparsed("player", target.getName());
                            var uuid = target.getUniqueId();
                            Tasks.async(plugin, () -> coins.get(uuid),
                                    balance -> sender.sendMessage(messages.get("coins.other", name, Formatter.number("coins", balance))),
                                    () -> failed(sender, messages));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    /** Works on offline accounts too, so the target is a name looked up in the database rather than an online player. */
    private static LiteralArgumentBuilder<CommandSourceStack> change(Plugin plugin, CoinsRepository coins, Messages messages,
                                                                    String literal, Change change, int minimum) {
        return Commands.literal(literal)
                .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            Bukkit.getOnlinePlayers().stream()
                                    .map(Player::getName)
                                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .then(Commands.argument("amount", IntegerArgumentType.integer(minimum))
                                .executes(context -> {
                                    var sender = context.getSource().getSender();
                                    String name = StringArgumentType.getString(context, "player");
                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                    Tasks.async(plugin, () -> apply(coins, change, name, amount),
                                            outcome -> sender.sendMessage(outcome
                                                    .map(done -> describe(messages, change, done, amount))
                                                    .orElseGet(() -> messages.get("coins.unknown", Placeholder.unparsed("player", name)))),
                                            () -> failed(sender, messages));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private static Optional<Outcome> apply(CoinsRepository coins, Change change, String name, int amount) {
        return coins.account(name).map(account -> {
            boolean applied = switch (change) {
                case GIVE -> {
                    coins.add(account.uuid(), account.name(), amount);
                    yield true;
                }
                case TAKE -> coins.withdraw(account.uuid(), account.name(), amount);
                case SET -> {
                    coins.set(account.uuid(), account.name(), amount);
                    yield true;
                }
            };
            return new Outcome(account, applied, coins.get(account.uuid()));
        });
    }

    private static Component describe(Messages messages, Change change, Outcome outcome, int amount) {
        var player = Placeholder.unparsed("player", outcome.account().name());
        var balance = Formatter.number("coins", outcome.coins());
        var changed = Formatter.number("amount", amount);
        return switch (change) {
            case GIVE -> messages.get("coins.given", player, changed, balance);
            case TAKE -> outcome.applied()
                    ? messages.get("coins.taken", player, changed, balance)
                    : messages.get("coins.not-enough", player, balance);
            case SET -> messages.get("coins.set", player, balance);
        };
    }

    private static void sendTop(CommandSender sender, Messages messages, List<CoinsRepository.Balance> top) {
        sender.sendMessage(messages.get("coins.top.separator"));
        sender.sendMessage(messages.get("coins.top.title"));
        if (top.isEmpty()) {
            sender.sendMessage(messages.get("coins.top.empty"));
        }
        int rank = 0;
        for (var entry : top) {
            rank++;
            sender.sendMessage(messages.get(rank == 1 ? "coins.top.first" : "coins.top.entry",
                    Formatter.number("rank", rank),
                    Placeholder.unparsed("name", entry.name()),
                    Formatter.number("coins", entry.coins())));
        }
        sender.sendMessage(messages.get("coins.top.separator"));
    }

    private static void failed(CommandSender sender, Messages messages) {
        sender.sendMessage(messages.get("operation.failed"));
    }
}
