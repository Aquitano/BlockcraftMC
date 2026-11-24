package me.blockcraft.ttt.shop;

import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BooleanSupplier;

/** Who walked past a motion sensor, newest first, each with how long ago. */
final class SensorLogDialog {

    record Sighting(String name, int secondsAgo) {
    }

    private SensorLogDialog() {
    }

    static void show(Player reader, Messages messages, List<Sighting> sightings, BooleanSupplier eligible) {
        var lines = sightings.isEmpty()
                ? List.of(messages.get("shop.motion-sensor.nobody"))
                : sightings.stream().map(sighting -> messages.get("shop.motion-sensor.entry",
                        Placeholder.unparsed("player", sighting.name()),
                        Formatter.number("minutes", sighting.secondsAgo() / 60),
                        Placeholder.unparsed("seconds", String.format("%02d", sighting.secondsAgo() % 60)))).toList();
        var body = DialogBody.plainMessage(Component.join(JoinConfiguration.newlines(), lines));
        var dialog = Dialogs.create(messages.get("shop.motion-sensor.title"), List.of(body), List.of(),
                Dialogs.actions(List.of(), Dialogs.button(BlockcraftApi.get().messages().get("menu.close"))));
        Dialogs.show(reader, dialog, eligible);
    }
}
