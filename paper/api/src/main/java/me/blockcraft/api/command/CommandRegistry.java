package me.blockcraft.api.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;

import java.util.List;

/** Registers Brigadier commands with the description from the module's {@code commands.<name>.description}. */
public final class CommandRegistry {

    private final Commands registrar;
    private final Messages messages;

    public CommandRegistry(Commands registrar, Messages messages) {
        this.registrar = registrar;
        this.messages = messages;
    }

    public void register(LiteralCommandNode<CommandSourceStack> command, String... aliases) {
        registrar.register(command, messages.raw("commands." + command.getLiteral() + ".description"), List.of(aliases));
    }
}
