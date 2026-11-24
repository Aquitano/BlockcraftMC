package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;

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
        var targets = new SelfOrOthers("speed", messages);
        return Commands.literal("speed")
                .requires(CorePermission.of("speed"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("speed.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(targets.executes(Commands.argument("level", IntegerArgumentType.integer(1, MAX_LEVEL)),
                                context -> speed(context, Optional.empty(), audit, messages))
                        .then(targets.executes(Commands.literal("walk"),
                                context -> speed(context, Optional.of(Kind.WALK), audit, messages)))
                        .then(targets.executes(Commands.literal("fly"),
                                context -> speed(context, Optional.of(Kind.FLY), audit, messages))))
                .build();
    }

    private static Optional<SelfOrOthers.Action> speed(CommandContext<CommandSourceStack> context, Optional<Kind> requested,
                                                       StaffAudit audit, Messages messages) {
        int level = IntegerArgumentType.getInteger(context, "level");
        return Optional.of((sender, target) -> {
            var kind = requested.orElse(target.isFlying() ? Kind.FLY : Kind.WALK);
            switch (kind) {
                case WALK -> target.setWalkSpeed(kind.speed(level));
                case FLY -> target.setFlySpeed(kind.speed(level));
            }
            var speed = Formatter.number("speed", level);
            target.sendMessage(messages.get(kind == Kind.FLY ? "speed.fly" : "speed.walk", speed));
            return SelfOrOthers.Outcome.told(kind == Kind.FLY ? "speed.fly-other" : "speed.walk-other", speed)
                    .audited(audit, "speed", kind.name().toLowerCase(Locale.ROOT) + " " + level);
        });
    }
}
