package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.RotationResolver;
import io.papermc.paper.entity.LookAnchor;
import io.papermc.paper.math.Rotation;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Vanilla's {@code /tp}: to an entity or a position, for the sender or for targets, with rotation or facing. Players
 * only move others below their rank, every moved player can return with {@code /back}, and staff moves are logged.
 */
public final class TpCommand {

    /** Where a move ends and what the sender is told it went to. */
    private record Destination(Function<Entity, Location> location, String label, Optional<Player> player) {
    }

    private TpCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(BackLocations back, StaffAudit audit, Messages messages) {
        return Commands.literal("tp")
                .requires(CorePermission.of("tp"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("tp.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("location", ArgumentTypes.finePosition())
                        .executes(context -> moveSelf(context, position(context, Optional.empty()), back, audit, messages)))
                .then(Commands.argument("destination", ArgumentTypes.entity())
                        .executes(context -> moveSelf(context, entity(context), back, audit, messages)))
                .then(Commands.argument("targets", ArgumentTypes.entities())
                        .then(Commands.argument("location", ArgumentTypes.finePosition())
                                .executes(context -> move(context, position(context, Optional.empty()), Optional.empty(), back, audit, messages))
                                .then(Commands.argument("rotation", ArgumentTypes.rotation())
                                        .executes(context -> move(context, position(context, Optional.of(
                                                context.getArgument("rotation", RotationResolver.class).resolve(context.getSource()))),
                                                Optional.empty(), back, audit, messages)))
                                .then(Commands.literal("facing")
                                        .then(Commands.literal("entity")
                                                .then(Commands.argument("facingEntity", ArgumentTypes.entity())
                                                        .executes(context -> move(context, position(context, Optional.empty()),
                                                                Optional.of(facing(context, LookAnchor.FEET)), back, audit, messages))
                                                        .then(Commands.argument("facingAnchor", ArgumentTypes.entityAnchor())
                                                                .executes(context -> move(context, position(context, Optional.empty()),
                                                                        Optional.of(facing(context, context.getArgument("facingAnchor", LookAnchor.class))),
                                                                        back, audit, messages)))))
                                        .then(Commands.argument("facingLocation", ArgumentTypes.finePosition())
                                                .executes(context -> move(context, position(context, Optional.empty()),
                                                        Optional.of(context.getArgument("facingLocation", FinePositionResolver.class)
                                                                .resolve(context.getSource()).toLocation(context.getSource().getLocation().getWorld())),
                                                        back, audit, messages)))))
                        .then(Commands.argument("destination", ArgumentTypes.entity())
                                .executes(context -> move(context, entity(context), Optional.empty(), back, audit, messages))))
                .build();
    }

    private static Destination position(CommandContext<CommandSourceStack> context, Optional<Rotation> rotation)
            throws CommandSyntaxException {
        var source = context.getSource();
        var target = context.getArgument("location", FinePositionResolver.class).resolve(source).toLocation(source.getLocation().getWorld());
        return new Destination(moved -> {
            var location = target.clone();
            location.setYaw(rotation.map(Rotation::yaw).orElse(moved.getLocation().getYaw()));
            location.setPitch(rotation.map(Rotation::pitch).orElse(moved.getLocation().getPitch()));
            return location;
        }, BackLocations.describe(target), Optional.empty());
    }

    private static Destination entity(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var destination = Players.entities(context, "destination").getFirst();
        return new Destination(moved -> destination.getLocation(), destination.getName(),
                destination instanceof Player player ? Optional.of(player) : Optional.empty());
    }

    private static Location facing(CommandContext<CommandSourceStack> context, LookAnchor anchor) throws CommandSyntaxException {
        var entity = Players.entities(context, "facingEntity").getFirst();
        return anchor == LookAnchor.EYES && entity instanceof LivingEntity living ? living.getEyeLocation() : entity.getLocation();
    }

    private static int moveSelf(CommandContext<CommandSourceStack> context, Destination destination, BackLocations back, StaffAudit audit,
                                Messages messages) {
        var sender = context.getSource().getSender();
        var self = context.getSource().getExecutor();
        if (self == null) {
            sender.sendMessage(messages.get("tp.usage"));
            return Command.SINGLE_SUCCESS;
        }
        if (back.teleport(self, destination.location().apply(self))) {
            self.sendMessage(messages.get("tp.self", Placeholder.unparsed("to", destination.label())));
            recordSelf(sender, destination, audit);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int move(CommandContext<CommandSourceStack> context, Destination destination, Optional<Location> facing,
                            BackLocations back, StaffAudit audit, Messages messages) throws CommandSyntaxException {
        var sender = context.getSource().getSender();
        List<Entity> moved = TargetGuard.permitted(sender, Players.entities(context, "targets"), messages).stream()
                .filter(target -> back.teleport(target, destination.location().apply(target)))
                .toList();
        for (var target : moved) {
            facing.ifPresent(point -> target.lookAt(point.getX(), point.getY(), point.getZ(), LookAnchor.EYES));
            if (target.equals(sender)) {
                recordSelf(sender, destination, audit);
            } else if (target instanceof Player player) {
                audit.record(sender, "tp", player, destination.label());
            }
        }
        var to = Placeholder.unparsed("to", destination.label());
        if (moved.size() == 1) {
            sender.sendMessage(messages.get("tp.other", Placeholder.component("player", moved.getFirst().name()), to));
        } else if (!moved.isEmpty()) {
            sender.sendMessage(messages.get("tp.many", Formatter.number("count", moved.size()), to));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static void recordSelf(CommandSender sender, Destination destination, StaffAudit audit) {
        destination.player().ifPresentOrElse(
                player -> audit.record(sender, "tp.to", player),
                () -> audit.record(sender, "tp.to", destination.label()));
    }
}
