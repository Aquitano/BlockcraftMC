package me.blockcraft.api.player;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/** The card every server shows on arrival: where the player is, on which map and who built it. */
public final class Arrival {

    private static final Title.Times TIMES = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500));

    private Arrival() {
    }

    public static void show(Player player, Component where) {
        show(player, where, Sounds.JOIN);
    }

    public static void show(Player player, Component where, Sounds sound) {
        var api = BlockcraftApi.get();
        var map = api.map();
        var messages = api.messages();
        var name = Placeholder.unparsed("map", map.name());
        var subtitle = map.builders().isEmpty()
                ? messages.get("arrival.subtitle", name)
                : messages.get("arrival.subtitle-credits", name, Placeholder.unparsed("builders", String.join(", ", map.builders())));
        api.notifications().reward(player, Title.title(messages.get("arrival.title", Placeholder.component("where", where)), subtitle, TIMES), sound);
    }
}
