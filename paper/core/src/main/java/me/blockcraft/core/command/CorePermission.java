package me.blockcraft.core.command;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

final class CorePermission {

    static final String BYPASS_RANK = "blockcraft.core.bypass-rank";

    private CorePermission() {
    }

    /** Any sender holding {@code blockcraft.core.<node>}: players, the console and rcon alike. */
    static Predicate<CommandSourceStack> of(String node) {
        return source -> source.getSender().hasPermission("blockcraft.core." + node);
    }

    static Predicate<CommandSourceStack> player(String command) {
        return source -> source.getSender() instanceof Player player && player.hasPermission("blockcraft.core." + command);
    }
}
