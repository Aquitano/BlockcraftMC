package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** {@code /gm <mode> [players]} plus the shortcuts {@code /gmc}, {@code /gms}, {@code /gma} and {@code /gmsp}. */
public final class GamemodeCommand {

    private static final List<String> SUGGESTIONS = List.of("survival", "creative", "adventure", "spectator", "0", "1", "2", "3");

    private GamemodeCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffAudit audit, Messages messages) {
        return Commands.literal("gm")
                .requires(CorePermission.of("gm"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("gm.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(withTargets(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    SUGGESTIONS.stream().filter(mode -> mode.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                                    return builder.buildFuture();
                                }),
                        context -> parse(StringArgumentType.getString(context, "mode")), audit, messages))
                .build();
    }

    /** A root command that always sets one mode, such as {@code /gmc}. */
    public static LiteralCommandNode<CommandSourceStack> shortcut(String name, GameMode mode, StaffAudit audit, Messages messages) {
        return withTargets(Commands.literal(name).requires(CorePermission.of("gm")), context -> Optional.of(mode), audit, messages).build();
    }

    static Optional<GameMode> parse(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> Optional.of(GameMode.SURVIVAL);
            case "1", "c", "creative" -> Optional.of(GameMode.CREATIVE);
            case "2", "a", "adventure" -> Optional.of(GameMode.ADVENTURE);
            case "3", "sp", "spectator" -> Optional.of(GameMode.SPECTATOR);
            default -> Optional.empty();
        };
    }

    @FunctionalInterface
    private interface ModeSource {
        Optional<GameMode> of(CommandContext<CommandSourceStack> context);
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withTargets(T node, ModeSource mode, StaffAudit audit, Messages messages) {
        return node
                .executes(context -> {
                    var chosen = chosen(context, mode, messages);
                    if (chosen.isPresent()) {
                        Players.self(context.getSource(), messages.get("gm.usage"))
                                .ifPresent(player -> change(player, player, chosen.get(), audit, messages));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("players", ArgumentTypes.players())
                        .requires(CorePermission.of("gm.others"))
                        .executes(context -> {
                            var chosen = chosen(context, mode, messages);
                            if (chosen.isPresent()) {
                                var sender = context.getSource().getSender();
                                TargetGuard.permitted(sender, Players.resolveAll(context, "players"), messages)
                                        .forEach(target -> change(sender, target, chosen.get(), audit, messages));
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static Optional<GameMode> chosen(CommandContext<CommandSourceStack> context, ModeSource mode, Messages messages) {
        var chosen = mode.of(context);
        if (chosen.isEmpty()) {
            context.getSource().getSender().sendMessage(messages.get("gm.usage"));
        }
        return chosen;
    }

    private static void change(CommandSender sender, Player target, GameMode mode, StaffAudit audit, Messages messages) {
        target.setGameMode(mode);
        var label = Placeholder.component("mode", label(mode, messages));
        target.sendMessage(messages.get("gm.changed", label));
        String action = "gamemode." + mode.name().toLowerCase(Locale.ROOT);
        if (target.equals(sender)) {
            audit.record(sender, action);
        } else {
            sender.sendMessage(messages.get("gm.changed-other", label, Placeholder.unparsed("player", target.getName())));
            audit.record(sender, action, target);
        }
    }

    private static Component label(GameMode mode, Messages messages) {
        return switch (mode) {
            case SURVIVAL -> messages.get("gm.mode.survival");
            case CREATIVE -> messages.get("gm.mode.creative");
            case ADVENTURE -> messages.get("gm.mode.adventure");
            case SPECTATOR -> messages.get("gm.mode.spectator");
        };
    }
}
