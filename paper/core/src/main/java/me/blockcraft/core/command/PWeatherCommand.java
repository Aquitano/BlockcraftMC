package me.blockcraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.WeatherType;

import java.util.Optional;

/** Weather of the player's own until reset or rejoin; the world and everyone else keep theirs. */
public final class PWeatherCommand {

    private PWeatherCommand() {
    }

    public static LiteralCommandNode<CommandSourceStack> create(Messages messages) {
        var targets = new SelfOrOthers("pweather", messages);
        var clear = weather(Optional.of(WeatherType.CLEAR), messages.get("pweather.clear"), messages);
        var rain = weather(Optional.of(WeatherType.DOWNFALL), messages.get("pweather.rain"), messages);
        return Commands.literal("pweather")
                .requires(CorePermission.of("pweather"))
                .executes(context -> {
                    context.getSource().getSender().sendMessage(messages.get("pweather.usage"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(targets.executes(Commands.literal("clear"), clear))
                .then(targets.executes(Commands.literal("rain"), rain))
                .then(targets.executes(Commands.literal("reset"), weather(Optional.empty(), Component.empty(), messages)))
                .build();
    }

    private static SelfOrOthers.Action weather(Optional<WeatherType> weather, Component label, Messages messages) {
        return (sender, target) -> {
            weather.ifPresentOrElse(target::setPlayerWeather, target::resetPlayerWeather);
            var shown = Placeholder.component("weather", label);
            target.sendMessage(messages.get(weather.isPresent() ? "pweather.set" : "pweather.reset", shown));
            return SelfOrOthers.Outcome.told(weather.isPresent() ? "pweather.set-other" : "pweather.reset-other", shown);
        };
    }
}
