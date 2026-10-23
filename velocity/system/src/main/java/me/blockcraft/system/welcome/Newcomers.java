package me.blockcraft.system.welcome;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.moderation.PlayerRepository;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.common.welcome.GreetingRepository;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.network.ServerDirectory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * A player's first join of the network: the join number they keep, the OG tag when the old network knew their
 * account, and the welcome committee. Everyone on a server of {@code welcome.groups} sees the newcomer announced,
 * those servers count greetings for the window, and once it closes the newcomer hears who said hello.
 */
public final class Newcomers {

    /** What the login found out about a newcomer, said once they are in. */
    public record Arrival(OptionalInt joinNumber, boolean og) {
    }

    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyMessageCodec.CHANNEL);
    /** A greeting typed in the window's last second still has to reach {@code bc_greetings} from its server. */
    private static final Duration WRITE_GRACE = Duration.ofSeconds(5);

    private final Object plugin;
    private final ProxyServer proxy;
    private final ServerDirectory directory;
    private final PlayerRepository players;
    private final GreetingRepository greetings;
    private final OgTag og;
    private final SystemConfig.Welcome config;
    private final Messages messages;
    private final Logger logger;

    public Newcomers(Object plugin, ProxyServer proxy, ServerDirectory directory, PlayerRepository players, GreetingRepository greetings,
                     OgTag og, SystemConfig.Welcome config, Messages messages, Logger logger) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.directory = directory;
        this.players = players;
        this.greetings = greetings;
        this.og = og;
        this.config = config;
        this.messages = messages;
        this.logger = logger;
    }

    /** Runs at login once the player's row exists; a database failure costs the newcomer a line, never the login. */
    public Arrival arrive(Player player) {
        return new Arrival(joinNumber(player), knownBefore(player));
    }

    public Arrival returning(Player player) {
        return new Arrival(OptionalInt.empty(), knownBefore(player));
    }

    public void welcome(Player newcomer, Arrival arrival) {
        if (config.enabled()) {
            arrival.joinNumber().ifPresent(number -> announce(newcomer, number));
        }
        if (arrival.og()) {
            messages.getLines("welcome.og").forEach(newcomer::sendMessage);
        }
    }

    private void announce(Player newcomer, int number) {
        var details = TagResolver.resolver(Placeholder.unparsed("player", newcomer.getUsername()),
                Formatter.number("number", number), Formatter.number("seconds", config.greetingSeconds()));
        var announcement = messages.getLines("welcome.announcement", details);
        for (Player other : proxy.getAllPlayers()) {
            if (!other.getUniqueId().equals(newcomer.getUniqueId())
                    && other.getCurrentServer().map(connection -> welcoming(connection.getServer())).orElse(false)) {
                announcement.forEach(other::sendMessage);
            }
        }
        messages.getLines("welcome.newcomer", details).forEach(newcomer::sendMessage);
        // A plugin message travels with a player, so a server nobody is on hears nothing; nobody there could greet.
        var counting = ProxyMessageCodec.encode(new ProxyMessage.Newcomer(newcomer.getUniqueId(), newcomer.getUsername(),
                config.greetingSeconds()));
        proxy.getAllServers().stream().filter(this::welcoming).forEach(server -> server.sendPluginMessage(CHANNEL, counting));
        proxy.getScheduler().buildTask(plugin, () -> thank(newcomer))
                .delay(Duration.ofSeconds(config.greetingSeconds()).plus(WRITE_GRACE))
                .schedule();
    }

    private void thank(Player newcomer) {
        if (!newcomer.isActive()) {
            return;
        }
        List<String> names;
        try {
            names = greetings.greeters(newcomer.getUniqueId());
        } catch (DatabaseException e) {
            logger.warn("Could not read who greeted {}", newcomer.getUsername(), e);
            return;
        }
        if (names.isEmpty()) {
            return;
        }
        var greeters = Greeters.of(names, config.listedGreeters());
        var parts = new ArrayList<Component>();
        greeters.named().forEach(name -> parts.add(Component.text(name)));
        if (greeters.others() > 0) {
            parts.add(messages.get("welcome.greeters.others", Formatter.number("count", greeters.others())));
        }
        var list = Placeholder.component("greeters", Component.join(JoinConfiguration.separators(
                messages.get("welcome.greeters.separator"), messages.get("welcome.greeters.last-separator")), parts));
        newcomer.sendMessage(names.size() == 1 ? messages.get("welcome.greeted-one", list) : messages.get("welcome.greeted", list));
    }

    private boolean welcoming(RegisteredServer server) {
        return directory.groupOf(server.getServerInfo().getName()).map(config.groups()::contains).orElse(false);
    }

    private OptionalInt joinNumber(Player player) {
        try {
            return players.assignJoinNumber(player.getUniqueId());
        } catch (DatabaseException e) {
            logger.warn("Could not give {} a join number", player.getUsername(), e);
            return OptionalInt.empty();
        }
    }

    private boolean knownBefore(Player player) {
        try {
            return og.thankIfKnown(player.getUniqueId());
        } catch (DatabaseException e) {
            logger.warn("Could not read the OG reward for {}", player.getUsername(), e);
            return false;
        }
    }
}
