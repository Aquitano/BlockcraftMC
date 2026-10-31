package me.blockcraft.core.command;

import me.blockcraft.api.rank.Ranks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/** Keeps staff from using player commands on someone who is their equal or above, and from silly self-targeting. */
final class TargetGuard {

    private TargetGuard() {
    }

    static boolean mayTarget(CommandSender sender, Player target, Messages messages) {
        if (allowed(sender, target)) {
            return true;
        }
        sender.sendMessage(messages.get("rank.protected"));
        return false;
    }

    /** The targets the sender may act on; a selector that also caught protected players says how many it skipped. */
    static <T extends Entity> List<T> permitted(CommandSender sender, List<T> targets, Messages messages) {
        var permitted = targets.stream().filter(target -> !(target instanceof Player player) || allowed(sender, player)).toList();
        int skipped = targets.size() - permitted.size();
        if (skipped > 0 && targets.size() == 1) {
            sender.sendMessage(messages.get("rank.protected"));
        } else if (skipped > 0) {
            sender.sendMessage(messages.get("rank.skipped", Formatter.number("count", skipped)));
        }
        return permitted;
    }

    static boolean notSelf(CommandSender sender, Player target, Messages messages) {
        if (!target.equals(sender)) {
            return true;
        }
        sender.sendMessage(messages.get("rank.self"));
        return false;
    }

    private static boolean allowed(CommandSender sender, Player target) {
        return !(sender instanceof Player actor) || actor.equals(target)
                || actor.hasPermission(CorePermission.BYPASS_RANK) || Ranks.outranks(actor, target);
    }
}
