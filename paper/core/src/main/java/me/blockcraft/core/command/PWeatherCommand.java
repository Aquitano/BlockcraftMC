package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.WeatherType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;

/** Weather of the player's own until reset or rejoin; the world and everyone else keep theirs. */
public final class PWeatherCommand {

    private PWeatherCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        return Commands.literal("pweather")
                .requires(CorePermission.of("pweather"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("pweather.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(weather("clear", Optional.of(WeatherType.CLEAR), messages.get("pweather.clear"), messages))
                .then(weather("rain", Optional.of(WeatherType.DOWNFALL), messages.get("pweather.rain"), messages))
                .then(weather("reset", Optional.empty(), Component.empty(), messages))
                .build();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> weather(String literal, Optional<WeatherType> weather, Component label,
                                                                      Messages messages) {
        return Commands.literal(literal)
                .executes(context -> {
                    Players.self(context.getSource(), messages.get("pweather.usage"))
                            .ifPresent(player -> apply(player, player, weather, label, messages));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("players", ArgumentTypes.players())
                        .requires(CorePermission.of("pweather.others"))
                        .executes(context -> {
                            var sender = context.getSource().getSender();
                            for (var target : TargetGuard.permitted(sender, Players.resolveAll(context, "players"), messages)) {
                                apply(sender, target, weather, label, messages);
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static void apply(CommandSender sender, Player target, Optional<WeatherType> weather, Component label, Messages messages) {
        weather.ifPresentOrElse(target::setPlayerWeather, target::resetPlayerWeather);
        var shown = Placeholder.component("weather", label);
        var player = Placeholder.unparsed("player", target.getName());
        target.sendMessage(messages.get(weather.isPresent() ? "pweather.set" : "pweather.reset", shown));
        if (!target.equals(sender)) {
            sender.sendMessage(messages.get(weather.isPresent() ? "pweather.set-other" : "pweather.reset-other", shown, player));
        }
    }
}
