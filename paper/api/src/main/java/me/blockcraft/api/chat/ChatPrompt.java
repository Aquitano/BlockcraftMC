package me.blockcraft.api.chat;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * The buttons of one question in chat, such as a purchase or a report: they act only for the player they were printed
 * for, and the first click spends them all, so the answer never runs twice.
 */
public final class ChatPrompt {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();

    private final Player viewer;
    private boolean answered;

    public ChatPrompt(Player viewer) {
        this.viewer = viewer;
    }

    public Component button(Component label, Component hover, Consumer<Player> action) {
        var plugin = JavaPlugin.getProvidingPlugin(ChatPrompt.class);
        return label.hoverEvent(HoverEvent.showText(hover)).clickEvent(ClickEvent.callback(audience -> {
            if (audience == viewer) {
                Tasks.sync(plugin, () -> {
                    if (!answered && viewer.isOnline()) {
                        answered = true;
                        Sounds.MENU_CLICK.play(viewer);
                        action.accept(viewer);
                    }
                });
            }
        }, ONCE));
    }
}
