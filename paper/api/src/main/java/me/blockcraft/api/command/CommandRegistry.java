package me.blockcraft.api.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers Brigadier commands with the description from the module's {@code commands.<name>.description} and
 * remembers every command registered on this server, so a help page can be drawn from what actually exists.
 */
public final class CommandRegistry {

    public record Entry(String name, Component description, LiteralCommandNode<CommandSourceStack> node) {
    }

    private static final Map<String, Entry> CATALOG = new ConcurrentHashMap<>();

    private final Commands registrar;
    private final Messages messages;

    public CommandRegistry(Commands registrar, Messages messages) {
        this.registrar = registrar;
        this.messages = messages;
    }

    public void register(LiteralCommandNode<CommandSourceStack> command, String... aliases) {
        String key = "commands." + command.getLiteral() + ".description";
        registrar.register(command, messages.raw(key), List.of(aliases));
        CATALOG.put(command.getLiteral(), new Entry(command.getLiteral(), messages.get(key), command));
    }

    /** Every registered command the source may run, alphabetically. */
    public static List<Entry> usableBy(CommandSourceStack source) {
        return CATALOG.values().stream()
                .filter(entry -> entry.node().canUse(source))
                .sorted(Comparator.comparing(Entry::name))
                .toList();
    }
}
