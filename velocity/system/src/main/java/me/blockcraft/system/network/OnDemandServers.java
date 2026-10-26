package me.blockcraft.system.network;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.GroupState;
import me.blockcraft.common.network.GroupStateRepository;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.network.OnDemand.Effect;
import me.blockcraft.system.network.OnDemand.Observation;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Game servers that run only while someone plays (README, "Server discovery and groups"). {@link OnDemand} decides;
 * this carries it out: it asks the waker to start and stop servers, closes a group in the {@link ServerDirectory}
 * before its server stops, sends the players who waited once it is up, and writes every group's state to
 * {@code bc_server_groups} for the lobbies' navigator and signs.
 */
public final class OnDemandServers {

    private final ProxyServer proxy;
    private final OnDemand onDemand;
    private final WakerClient waker;
    private final ServerDirectory directory;
    private final GroupStateRepository states;
    private final Messages messages;
    private final Logger logger;
    private final InstantSource clock = InstantSource.system();
    /** One writer, so the table never ends on an older state than the last one decided. */
    private final ExecutorService writes = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("BlockcraftSystem on-demand states").daemon().factory());
    private volatile LobbyConnector lobby;

    public OnDemandServers(ProxyServer proxy, OnDemand onDemand, WakerClient waker, ServerDirectory directory,
                           GroupStateRepository states, Messages messages, Logger logger) {
        this.proxy = proxy;
        this.onDemand = onDemand;
        this.waker = waker;
        this.directory = directory;
        this.states = states;
        this.messages = messages;
        this.logger = logger;
        writes.execute(() -> write(() -> states.retain(onDemand.groups())));
    }

    /** The connector that sends players who waited; it is built with this, so it is handed over once both exist. */
    public void connect(LobbyConnector lobby) {
        this.lobby = lobby;
    }

    /**
     * A player wants into {@code group} and no server has room. True if on-demand took it on and told the player:
     * the group's server starts, or is already on its way; false leaves the player to the usual messages.
     */
    public boolean request(Player player, String group, TagResolver game) {
        var outcome = onDemand.request(group, player.getUniqueId(), clock.instant());
        if (outcome.isEmpty()) {
            return false;
        }
        player.sendMessage(messages.get(switch (outcome.get().reply()) {
            case STARTING -> "play.starting";
            case WAITING -> "play.waiting";
            case BUSY -> "play.start-failed";
        }, game));
        apply(outcome.get().effects());
        return true;
    }

    /** After every poll of {@code bc_servers}, once the directory holds what it found. */
    public void poll() {
        var seen = new HashMap<String, Observation>();
        for (var group : onDemand.groups()) {
            seen.put(group, new Observation(directory.running(group), directory.idle(group)));
        }
        apply(onDemand.observe(seen, clock.instant()));
    }

    private void apply(List<Effect> effects) {
        for (var effect : effects) {
            switch (effect) {
                case Effect.Publish publish -> {
                    if (publish.state() != GroupState.DRAINING) {
                        directory.undrain(publish.group());
                    }
                    writes.execute(() -> write(() -> states.put(publish.group(), publish.state())));
                }
                case Effect.Start start -> waker.start(start.group()).thenAccept(started -> {
                    if (!started) {
                        apply(onDemand.startFailed(start.group(), clock.instant()));
                    }
                });
                case Effect.Stop stop -> {
                    // Someone took a seat between the poll and now: the group stays awake.
                    if (!directory.drainIfIdle(stop.group())) {
                        apply(onDemand.stopped(stop.group(), false, clock.instant()));
                    } else {
                        waker.stop(stop.group()).thenAccept(stopped -> apply(onDemand.stopped(stop.group(), stopped, clock.instant())));
                    }
                }
                case Effect.Admit admit -> admit.players().forEach(id -> proxy.getPlayer(id)
                        .filter(player -> lobby != null && lobby.isOnLobby(player))
                        .ifPresent(player -> lobby.play(player, admit.group())));
                case Effect.Fail fail -> fail.players().forEach(id -> proxy.getPlayer(id).ifPresent(player ->
                        player.sendMessage(messages.get("play.start-failed", lobby == null ? TagResolver.empty() : lobby.game(fail.group())))));
            }
        }
    }

    private void write(Runnable write) {
        try {
            write.run();
        } catch (DatabaseException e) {
            logger.warn("Could not write bc_server_groups; the lobbies show the last state until the next change", e);
        }
    }
}
