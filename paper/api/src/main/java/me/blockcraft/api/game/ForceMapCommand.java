package me.blockcraft.api.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * /forcemap: staff decide the lobby's vote while it is still open. The game names what can be forced
 * ({@code options}) and forces it, returning the name of what it forced or nothing for an unknown option.
 */
public final class ForceMapCommand {

    private ForceMapCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages gameMessages, Supplier<List<String>> options,
                                                                BooleanSupplier open,
                                                                Function<String, Optional<Component>> force) {
        var messages = BlockcraftApi.get().messages().prefixedLike(gameMessages);
        return Commands.literal("forcemap")
                .requires(source -> source.getSender().hasPermission(GamePermissions.FORCE_MAP))
                .then(Commands.argument("option", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            options.get().stream().filter(key -> key.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var sender = context.getSource().getSender();
                            String key = StringArgumentType.getString(context, "option");
                            if (!open.getAsBoolean()) {
                                sender.sendMessage(messages.get("forcemap.closed"));
                                return Command.SINGLE_SUCCESS;
                            }
                            force.apply(key).ifPresentOrElse(
                                    forced -> sender.sendMessage(messages.get("forcemap.done", Placeholder.component("option", forced))),
                                    () -> sender.sendMessage(messages.get("forcemap.unknown", Placeholder.unparsed("option", key),
                                            Placeholder.unparsed("options", String.join(", ", options.get())))));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }
}
