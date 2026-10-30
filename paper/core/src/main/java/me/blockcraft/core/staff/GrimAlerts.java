package me.blockcraft.core.staff;

import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.event.events.CommandExecuteEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * GrimAC's alerts for the whole team: the {@code [staff] %vl%} command in Grim's punishments.yml becomes one staff
 * notice through the proxy, at most once per player and check within the cooldown. Grim would run the command on
 * the console, so it is cancelled here; Grim checks off the server thread, so the notice leaves on the next tick.
 */
public final class GrimAlerts {

    public static final String NOTIFY_PERMISSION = "blockcraft.core.anticheat.notify";
    private static final String COMMAND = "[staff]";

    private record Flag(UUID player, String check) {
    }

    private record Notice(String name, String check, String description, String violations, String verbose) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final String serverName;
    private final long cooldownNanos;
    private final Map<Flag, Long> lastNotice = new ConcurrentHashMap<>();

    private GrimAlerts(Plugin plugin, Messages messages, String serverName, Duration cooldown) {
        this.plugin = plugin;
        this.messages = messages;
        this.serverName = serverName;
        this.cooldownNanos = cooldown.toNanos();
    }

    public static void register(Plugin plugin, Messages messages, String serverName, Duration cooldown) {
        var alerts = new GrimAlerts(plugin, messages, serverName, cooldown);
        var grim = GrimAPIProvider.get();
        grim.getEventBus().get(CommandExecuteEvent.class).onCommandExecuteSupplier(grim.getGrimPlugin(plugin), alerts::onCommand);
    }

    private boolean onCommand(GrimUser user, AbstractCheck check, Supplier<String> verbose, String command, boolean cancelled) {
        if (!command.startsWith(COMMAND)) {
            return cancelled;
        }
        var flag = new Flag(user.getUniqueId(), check.getCheckName());
        if (due(flag)) {
            var notice = new Notice(user.getName(), check.getCheckName(), check.getDescription(),
                    command.substring(COMMAND.length()).strip(), verbose.get());
            Tasks.sync(plugin, () -> send(flag.player(), notice));
        }
        return true;
    }

    private boolean due(Flag flag) {
        long now = System.nanoTime();
        return lastNotice.merge(flag, now, (before, current) -> current - before >= cooldownNanos ? current : before) == now;
    }

    private void send(UUID flagged, Notice notice) {
        var player = Bukkit.getPlayer(flagged);
        String name = player != null ? BlockcraftApi.get().realName(player) : notice.name();
        var playerName = Placeholder.unparsed("player", name);
        var line = messages.get("anticheat.notice", playerName,
                        Placeholder.unparsed("check", notice.check()),
                        Placeholder.unparsed("vl", notice.violations()),
                        Placeholder.unparsed("server", serverName))
                .hoverEvent(HoverEvent.showText(messages.get("anticheat.details",
                        Placeholder.unparsed("description", notice.description()),
                        Placeholder.unparsed("verbose", notice.verbose()))))
                .append(Component.space())
                .append(messages.get("chat.actions.goto")
                        .clickEvent(ClickEvent.runCommand("/goto " + name))
                        .hoverEvent(HoverEvent.showText(messages.get("chat.actions.goto-hover", playerName))));
        Stream.concat(Stream.ofNullable(player), Bukkit.getOnlinePlayers().stream()).findFirst().ifPresentOrElse(
                carrier -> ProxyMessaging.notifyStaff(plugin, carrier, NOTIFY_PERMISSION, line),
                () -> plugin.getLogger().warning("Nobody was online to carry the anticheat notice about " + name));
    }
}
