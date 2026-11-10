package me.blockcraft.core.staff;

import ac.grim.grimac.api.AbstractCheck;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.event.events.CommandExecuteEvent;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.replay.ReplayRecording;
import me.blockcraft.common.anticheat.AnticheatEvidence;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicesManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
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

    private final Plugin plugin;
    private final Messages messages;
    private final String serverName;
    private final AnticheatHistory history;
    private final ServicesManager services;
    private final long cooldownNanos;
    private final Cache<Flag, Long> lastNotice = CacheBuilder.newBuilder().maximumSize(4096)
            .expireAfterWrite(Duration.ofHours(1)).build();
    private final ArrayBlockingQueue<AnticheatEvidence> pending;

    private GrimAlerts(Plugin plugin, Messages messages, String serverName, Duration cooldown,
                       AnticheatHistory history, int capacity) {
        this.plugin = plugin;
        this.messages = messages;
        this.serverName = serverName;
        this.history = history;
        this.services = plugin.getServer().getServicesManager();
        this.cooldownNanos = cooldown.toNanos();
        this.pending = new ArrayBlockingQueue<>(capacity);
    }

    public static void register(Plugin plugin, Messages messages, String serverName, Duration cooldown,
                                AnticheatHistory history, int capacity) {
        var alerts = new GrimAlerts(plugin, messages, serverName, cooldown, history, capacity);
        var grim = GrimAPIProvider.get();
        grim.getEventBus().get(CommandExecuteEvent.class).onCommandExecuteSupplier(grim.getGrimPlugin(plugin), alerts::onCommand);
        Bukkit.getScheduler().runTaskTimer(plugin, alerts::drain, 1L, 1L);
    }

    private boolean onCommand(GrimUser user, AbstractCheck check, Supplier<String> verbose, String command, boolean cancelled) {
        if (!command.startsWith(COMMAND)) {
            return cancelled;
        }
        String checkName = check.getCheckName();
        checkName = checkName.substring(0, Math.min(checkName.length(), 64));
        var flag = new Flag(user.getUniqueId(), checkName);
        if (due(flag)) {
            var recording = services.load(ReplayRecording.class);
            var replay = recording == null ? Optional.<AnticheatEvidence.Replay>empty()
                    : recording.position(flag.player()).map(position -> new AnticheatEvidence.Replay(position.replay(), position.tick()));
            var notice = new AnticheatEvidence(flag.player(), user.getName(), serverName, Instant.now(), checkName,
                    check.getDescription(), command.substring(COMMAND.length()).strip(), verbose.get(), replay);
            if (!pending.offer(notice)) {
                history.warn("Anticheat notice queue is full; some notices will not be delivered or stored", null);
            }
        }
        return true;
    }

    private boolean due(Flag flag) {
        long now = System.nanoTime();
        return lastNotice.asMap().merge(flag, now, (before, current) -> current - before >= cooldownNanos ? current : before) == now;
    }

    private void drain() {
        for (int count = 0; count < 32; count++) {
            var notice = pending.poll();
            if (notice == null) {
                return;
            }
            send(notice);
        }
    }

    private void send(AnticheatEvidence notice) {
        var player = Bukkit.getPlayer(notice.player());
        String name = player != null ? BlockcraftApi.get().realName(player) : notice.name();
        history.record(new AnticheatEvidence(notice.player(), name, serverName, notice.recordedAt(), notice.check(),
                notice.description(), notice.violations(), notice.verbose(), notice.replay()));
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
                () -> history.warn("Nobody was online to carry an anticheat notice; its history was queued", null));
    }
}
