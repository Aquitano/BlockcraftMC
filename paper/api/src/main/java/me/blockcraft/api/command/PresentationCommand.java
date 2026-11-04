package me.blockcraft.api.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.dialogue.Dialogues;
import me.blockcraft.api.presentation.VisualEffects;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.Consumer;

public final class PresentationCommand {
    private PresentationCommand() {}

    public static LiteralCommandNode<CommandSourceStack> create(Dialogues dialogues, VisualEffects visuals, Messages messages) {
        var command = Commands.literal("presentation")
                .requires(source -> source.getSender() instanceof Player player && player.hasPermission("blockcraft.presentation"))
                .executes(context -> {
                    context.getSource().getPlayerOrThrow().sendMessage(messages.get("presentation.help"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(action("dialogue", dialogues::startDemo))
                .then(action("advance", player -> {
                    if (dialogues.active(player)) dialogues.advance(player);
                    else player.sendMessage(messages.get("presentation.inactive"));
                }))
                .then(action("next", player -> dialogues.select(player, 1)))
                .then(action("previous", player -> dialogues.select(player, -1)))
                .then(action("stop", player -> {
                    dialogues.stop(player);
                    visuals.stop(player);
                    player.sendMessage(messages.get("presentation.stopped"));
                }));
        for (var style : VisualEffects.Style.values()) {
            String name = style.name().toLowerCase(Locale.ROOT);
            command.then(effect(name, style, visuals, messages));
        }
        command.then(effect("effects", VisualEffects.Style.HOLOGRAM, visuals, messages));
        command.then(effect("screen", VisualEffects.Style.CINEMATIC, visuals, messages));
        return command.build();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> effect(String name, VisualEffects.Style style,
                                                                       VisualEffects visuals, Messages messages) {
        return action(name, player -> {
            if (visuals.demo(player, style)) {
                player.sendMessage(messages.get("presentation.started", Placeholder.component("effect",
                        messages.get("presentation.labels." + style.name().toLowerCase(Locale.ROOT)))));
            }
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> action(String name, Consumer<Player> action) {
        return Commands.literal(name).executes(context -> {
            action.accept(context.getSource().getPlayerOrThrow());
            return Command.SINGLE_SUCCESS;
        });
    }
}
