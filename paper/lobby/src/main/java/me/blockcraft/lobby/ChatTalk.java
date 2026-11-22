package me.blockcraft.lobby;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The lobby's figures and boards answer a click with lines in chat. The same player clicking the same one again within
 * the cooldown gets nothing, and their buttons act only for the player they were printed for.
 */
public final class ChatTalk {

    /** Long enough to read the lines and scroll back to them, short enough that old offers stop working. */
    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final ClickCallback.Options REPEATABLE = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES).lifetime(LIFETIME).build();
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder()
            .uses(1).lifetime(LIFETIME).build();

    private record Told(UUID player, Object about) {
    }

    private final Plugin plugin;
    private final Duration cooldown;
    private final InstantSource clock;
    private final Map<Told, Instant> told = new HashMap<>();

    public ChatTalk(Plugin plugin, Duration cooldown) {
        this(plugin, cooldown, InstantSource.system());
    }

    ChatTalk(Plugin plugin, Duration cooldown, InstantSource clock) {
        this.plugin = plugin;
        this.cooldown = cooldown;
        this.clock = clock;
    }

    /** A line {@code speaker} says, in the lobby's {@code talk} format. */
    public static Component says(Messages messages, Component speaker, Component line) {
        return messages.get("talk", Placeholder.component("speaker", speaker), Placeholder.component("line", line));
    }

    /** Whether {@code about} talks to the player now, which it does at most once per cooldown. */
    public boolean ready(Player player, Object about) {
        return ready(player.getUniqueId(), about);
    }

    boolean ready(UUID player, Object about) {
        var now = clock.instant();
        told.values().removeIf(at -> !at.plus(cooldown).isAfter(now));
        return told.putIfAbsent(new Told(player, about), now) == null;
    }

    public Component button(Player viewer, Component label, Component hover, Consumer<Player> action) {
        return button(viewer, label, hover, action, REPEATABLE);
    }

    /** A button that works for one click, for an action such as paying out that must not run twice. */
    public Component once(Player viewer, Component label, Component hover, Consumer<Player> action) {
        return button(viewer, label, hover, action, ONCE);
    }

    private Component button(Player viewer, Component label, Component hover, Consumer<Player> action, ClickCallback.Options options) {
        return label.hoverEvent(HoverEvent.showText(hover)).clickEvent(ClickEvent.callback(audience -> {
            if (audience == viewer) {
                Tasks.sync(plugin, () -> {
                    if (viewer.isOnline()) {
                        Sounds.MENU_CLICK.play(viewer);
                        action.accept(viewer);
                    }
                });
            }
        }, options));
    }
}
