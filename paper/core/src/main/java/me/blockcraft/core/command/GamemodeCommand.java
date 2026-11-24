package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;

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
                .then(new SelfOrOthers("gm", messages).executes(Commands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    SUGGESTIONS.stream().filter(mode -> mode.startsWith(builder.getRemainingLowerCase())).forEach(builder::suggest);
                                    return builder.buildFuture();
                                }),
                        context -> parse(StringArgumentType.getString(context, "mode")).map(mode -> change(mode, audit, messages))))
                .build();
    }

    /** A root command that always sets one mode, such as {@code /gmc}. */
    public static LiteralCommandNode<CommandSourceStack> shortcut(String name, GameMode mode, StaffAudit audit, Messages messages) {
        return new SelfOrOthers("gm", messages)
                .executes(Commands.literal(name).requires(CorePermission.of("gm")), change(mode, audit, messages))
                .build();
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

    private static SelfOrOthers.Action change(GameMode mode, StaffAudit audit, Messages messages) {
        return (sender, target) -> {
            target.setGameMode(mode);
            var label = Placeholder.component("mode", label(mode, messages));
            target.sendMessage(messages.get("gm.changed", label));
            String action = "gamemode." + mode.name().toLowerCase(Locale.ROOT);
            if (target.equals(sender)) {
                audit.record(sender, action);
            }
            return SelfOrOthers.Outcome.told("gm.changed-other", label).audited(audit, action);
        };
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
