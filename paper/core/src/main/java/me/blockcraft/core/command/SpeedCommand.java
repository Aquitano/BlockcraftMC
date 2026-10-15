package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Optional;

/**
 * {@code /speed <1-10> [walk|fly] [players]}, Essentials style: 1 is the vanilla speed, 10 the fastest the client
 * allows. Without walk or fly it sets the fly speed of a flying target and the walk speed of everyone else.
 */
public final class SpeedCommand {

    static final int MAX_LEVEL = 10;

    enum Kind {
        WALK(0.2f), FLY(0.1f);

        private final float vanilla;

        Kind(float vanilla) {
            this.vanilla = vanilla;
        }

        /** Level 1 is vanilla, every further level adds an equal share of the way to the client's maximum of 1. */
        float speed(int level) {
            return vanilla + (level - 1) / (float) (MAX_LEVEL - 1) * (1f - vanilla);
        }
    }

    private SpeedCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffAudit audit, Messages messages) {
        return Commands.literal("speed")
                .requires(CorePermission.of("speed"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("speed.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(withTargets(Commands.argument("level", IntegerArgumentType.integer(1, MAX_LEVEL)), Optional.empty(), audit, messages)
                        .then(withTargets(Commands.literal("walk"), Optional.of(Kind.WALK), audit, messages))
                        .then(withTargets(Commands.literal("fly"), Optional.of(Kind.FLY), audit, messages)))
                .build();
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withTargets(T node, Optional<Kind> kind, StaffAudit audit,
                                                                                  Messages messages) {
        return node
                .executes(context -> {
                    int level = IntegerArgumentType.getInteger(context, "level");
                    Players.self(context.getSource(), messages.get("speed.usage"))
                            .ifPresent(player -> apply(player, player, level, kind, audit, messages));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("players", ArgumentTypes.players())
                        .requires(CorePermission.of("speed.others"))
                        .executes(context -> others(context, kind, audit, messages)));
    }

    private static int others(CommandContext<CommandSourceStack> context, Optional<Kind> kind, StaffAudit audit, Messages messages)
            throws CommandSyntaxException {
        var sender = context.getSource().getSender();
        int level = IntegerArgumentType.getInteger(context, "level");
        TargetGuard.permitted(sender, Players.resolveAll(context, "players"), messages)
                .forEach(target -> apply(sender, target, level, kind, audit, messages));
        return Command.SINGLE_SUCCESS;
    }

    private static void apply(CommandSender sender, Player target, int level, Optional<Kind> requested, StaffAudit audit, Messages messages) {
        var kind = requested.orElse(target.isFlying() ? Kind.FLY : Kind.WALK);
        switch (kind) {
            case WALK -> target.setWalkSpeed(kind.speed(level));
            case FLY -> target.setFlySpeed(kind.speed(level));
        }
        var speed = Formatter.number("speed", level);
        target.sendMessage(messages.get(kind == Kind.FLY ? "speed.fly" : "speed.walk", speed));
        if (!target.equals(sender)) {
            sender.sendMessage(messages.get(kind == Kind.FLY ? "speed.fly-other" : "speed.walk-other", speed,
                    Placeholder.unparsed("player", target.getName())));
            audit.record(sender, "speed", target, kind.name().toLowerCase(Locale.ROOT) + " " + level);
        }
    }
}
