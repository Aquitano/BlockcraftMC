package me.blockcraft.core.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

/** {@code /fly [players] [on|off]}: without on or off every target's flight flips. */
public final class FlyCommand {

    private static final Predicate<Player> TOGGLE = player -> !BlockcraftApi.get().flight().granted(player);
    private static final Predicate<Player> ON = player -> true;
    private static final Predicate<Player> OFF = player -> false;

    private FlyCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffAudit audit, Messages messages) {
        var targets = new SelfOrOthers("fly", messages);
        var toggle = fly(TOGGLE, audit, messages);
        var on = fly(ON, audit, messages);
        var off = fly(OFF, audit, messages);
        return Commands.literal("fly")
                .requires(CorePermission.of("fly"))
                .executes(targets.self(toggle))
                .then(Commands.literal("on").executes(targets.self(on)))
                .then(Commands.literal("off").executes(targets.self(off)))
                .then(targets.players()
                        .executes(targets.others(toggle))
                        .then(Commands.literal("on").executes(targets.others(on)))
                        .then(Commands.literal("off").executes(targets.others(off))))
                .build();
    }

    private static SelfOrOthers.Action fly(Predicate<Player> flight, StaffAudit audit, Messages messages) {
        return (sender, target) -> {
            boolean flying = flight.test(target);
            BlockcraftApi.get().flight().set(target, flying);
            target.setAllowFlight(flying);
            target.setFlying(flying);
            target.sendMessage(messages.get(flying ? "fly.enabled" : "fly.disabled"));
            return SelfOrOthers.Outcome.told(flying ? "fly.other-enabled" : "fly.other-disabled")
                    .audited(audit, flying ? "fly.on" : "fly.off");
        };
    }
}
