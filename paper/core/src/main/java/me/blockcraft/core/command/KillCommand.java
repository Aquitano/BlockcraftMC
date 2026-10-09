package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Vanilla's {@code /kill [targets]}, with the rank guard for players and German feedback. */
public final class KillCommand {

    private KillCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(StaffAudit audit, Messages messages) {
        return Commands.literal("kill")
                .requires(CorePermission.of("kill"))
                .executes(context -> {
                    Players.self(context.getSource(), messages.get("kill.usage")).ifPresent(player -> {
                        kill(player);
                        player.sendMessage(messages.get("kill.self"));
                    });
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("targets", ArgumentTypes.entities())
                        .executes(context -> {
                            var sender = context.getSource().getSender();
                            var targets = TargetGuard.permitted(sender, Players.entities(context, "targets"), messages);
                            targets.forEach(KillCommand::kill);
                            int others = 0;
                            for (Entity target : targets) {
                                if (target instanceof Player player && !player.equals(sender)) {
                                    audit.record(sender, "kill", player);
                                } else if (!(target instanceof Player)) {
                                    others++;
                                }
                            }
                            if (others > 0) {
                                audit.record(sender, "kill.entities", String.valueOf(others));
                            }
                            if (targets.size() == 1) {
                                sender.sendMessage(messages.get("kill.done", Placeholder.component("player", targets.getFirst().name())));
                            } else if (!targets.isEmpty()) {
                                sender.sendMessage(messages.get("kill.entities", Formatter.number("count", targets.size())));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private static void kill(Entity entity) {
        if (entity instanceof LivingEntity living) {
            living.setHealth(0.0);
        } else {
            entity.remove();
        }
    }
}
