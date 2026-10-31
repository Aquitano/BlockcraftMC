package me.blockcraft.system.duel;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.LiveSession;
import me.blockcraft.system.LobbyConnector;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.command.CommandSupport;
import me.blockcraft.system.ignore.IgnoreList;
import me.blockcraft.system.network.OnDemandServers;
import me.blockcraft.system.network.ServerDirectory;
import me.blockcraft.system.party.PartyManager;
import me.blockcraft.system.ranked.RankedQueue;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DuelChallenges {
    public record Settings(int setupSeconds, int inviteSeconds, int transferSeconds, int cooldownSeconds) { }
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyMessageCodec.CHANNEL);

    private static final class Challenge {
        final UUID ticket = UUID.randomUUID();
        final Player sender;
        final Player target;
        final Set<UUID> players;
        Instant expires;
        String server;
        boolean opened;
        boolean invited;
        boolean accepted;
        boolean cancelled;
        int transfers;

        Challenge(Player sender, Player target, Instant expires) {
            this.sender = sender;
            this.target = target;
            this.players = Set.of(sender.getUniqueId(), target.getUniqueId());
            this.expires = expires;
        }
    }

    private final Map<UUID, Challenge> pending = new HashMap<>();
    private final Map<UUID, Instant> cooldowns = new HashMap<>();
    private final ProxyServer proxy;
    private final ServerDirectory directory;
    private final LobbyConnector lobby;
    private final PartyManager parties;
    private final RankedQueue ranked;
    private final PlayerVisibility visibility;
    private final PlayerChoices choices;
    private final IgnoreList ignores;
    private final Optional<OnDemandServers> onDemand;
    private final Messages messages;
    private final Clock clock;
    private final String group;
    private final Settings settings;

    public DuelChallenges(ProxyServer proxy, ServerDirectory directory, LobbyConnector lobby, PartyManager parties,
                          RankedQueue ranked, PlayerVisibility visibility, PlayerChoices choices, IgnoreList ignores,
                          Optional<OnDemandServers> onDemand, Messages messages, Clock clock, me.blockcraft.system.SystemConfig config) {
        this.proxy = proxy;
        this.directory = directory;
        this.lobby = lobby;
        this.parties = parties;
        this.ranked = ranked;
        this.visibility = visibility;
        this.choices = choices;
        this.ignores = ignores;
        this.onDemand = onDemand;
        this.messages = messages;
        this.clock = clock;
        this.group = config.ranked().queue().group();
        this.settings = config.duels();
    }

    public BrigadierCommand command(CommandSupport support) {
        var root = BrigadierCommand.literalArgumentBuilder("duel").executes(support.asPlayer((player, context) -> openSetup(player)));
        for (String action : new String[]{"accept", "decline", "cancel"}) {
            root.then(BrigadierCommand.literalArgumentBuilder(action)
                    .executes(support.asPlayer((player, context) -> answerCurrent(player, action)))
                    .then(BrigadierCommand.requiredArgumentBuilder("ticket", StringArgumentType.word())
                            .executes(support.asPlayer((player, context) -> answer(player, action, StringArgumentType.getString(context, "ticket"))))));
        }
        root.then(support.playerArgument("player").executes(support.asPlayer((player, context) ->
                request(player, StringArgumentType.getString(context, "player")))));
        return new BrigadierCommand(root);
    }

    private synchronized void openSetup(Player player) {
        var challenge = pending.get(player.getUniqueId());
        if (challenge != null && challenge.sender == player && challenge.opened && !challenge.invited
                && here(player).filter(server -> server.equals(challenge.server)).isPresent()) {
            send(player, new ProxyMessage.DuelSetup(challenge.ticket, challenge.target.getUniqueId(), visibility.shownName(challenge.target)));
        } else player.sendMessage(messages.get("duel.usage"));
    }

    private synchronized void answerCurrent(Player player, String action) {
        var challenge = pending.get(player.getUniqueId());
        if (challenge == null) player.sendMessage(messages.get("duel.expired"));
        else answer(player, action, challenge.ticket.toString());
    }

    private boolean available(Player player) {
        return player.isActive() && here(player).filter(directory::isLobby).isPresent()
                && !parties.isInParty(player.getUniqueId()) && !ranked.isWaiting(player.getUniqueId())
                && !visibility.isVanished(player);
    }

    private Optional<String> here(Player player) {
        return player.getCurrentServer().map(connection -> connection.getServerInfo().getName());
    }

    private boolean reachable(Player sender, Player target) {
        return visibility.canSee(sender, target) && !ignores.ignores(sender, target) && !ignores.ignores(target, sender)
                && choices.enabled(target, PlayerSetting.DUEL_INVITES);
    }

    public synchronized void request(Player sender, String name) {
        var candidates = visibility.matches(sender, name);
        var target = candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.<Player>empty();
        if (target.isEmpty() || sender.equals(target.get()) || !available(sender) || !available(target.get())
                || !reachable(sender, target.get()) || pending.containsKey(sender.getUniqueId()) || pending.containsKey(target.get().getUniqueId())
                || clock.instant().isBefore(cooldowns.getOrDefault(sender.getUniqueId(), Instant.MIN))) {
            sender.sendMessage(messages.get("duel.unavailable"));
            return;
        }
        var challenge = new Challenge(sender, target.get(), clock.instant().plusSeconds(settings.setupSeconds()));
        cooldowns.put(sender.getUniqueId(), clock.instant().plusSeconds(settings.cooldownSeconds()));
        challenge.players.forEach(id -> pending.put(id, challenge));
        sender.sendMessage(messages.get("duel.preparing"));
        prepare(challenge);
    }

    private void prepare(Challenge challenge) {
        var server = directory.reserveDuel(group, challenge.players);
        if (server.isEmpty()) {
            onDemand.ifPresent(servers -> servers.wake(group));
            return;
        }
        challenge.server = server.get().getServerInfo().getName();
        transfer(challenge, challenge.sender, server.get());
    }

    private void transfer(Challenge challenge, Player player, RegisteredServer server) {
        challenge.transfers++;
        directory.send(player, server).thenAccept(arrived -> transferred(challenge, player, arrived));
    }

    private synchronized void transferred(Challenge challenge, Player player, boolean arrived) {
        challenge.transfers--;
        if (!arrived) cancel(challenge);
        if (challenge.cancelled) {
            if (arrived) returnToLobby(challenge, player);
            if (challenge.transfers == 0) directory.releaseDuel(challenge.server, challenge.players);
        }
    }

    public synchronized void invite(Player sender, ProxyMessage.DuelInvitation invitation) {
        var challenge = pending.get(sender.getUniqueId());
        if (challenge == null || challenge.sender != sender || !challenge.ticket.equals(invitation.ticket())
                || !here(sender).filter(server -> server.equals(challenge.server)).isPresent()
                || challenge.invited || !clock.instant().isBefore(challenge.expires)
                || !available(challenge.target) || !reachable(sender, challenge.target)) {
            sender.sendMessage(messages.get("duel.expired"));
            return;
        }
        challenge.invited = true;
        challenge.expires = clock.instant().plusSeconds(settings.inviteSeconds());
        var rules = invitation.rules();
        challenge.target.sendMessage(messages.get("duel.invitation", Placeholder.unparsed("player", visibility.shownName(sender)),
                Placeholder.component("mode", messages.get("duel.mode." + rules.mode())),
                Placeholder.unparsed("map", invitation.mapName()),
                Placeholder.component("combat", messages.get(rules.legacy() ? "duel.legacy" : "duel.modern")),
                Placeholder.component("rating", messages.get(rules.rated() ? "duel.rated" : "duel.unrated")),
                Formatter.number("games", rules.bestOf()), Formatter.number("seconds", settings.inviteSeconds())));
        challenge.target.sendMessage(messages.get("duel.accept").clickEvent(ClickEvent.runCommand("/duel accept " + challenge.ticket))
                .append(messages.get("duel.decline").clickEvent(ClickEvent.runCommand("/duel decline " + challenge.ticket))));
        sender.sendMessage(messages.get("duel.sent").clickEvent(ClickEvent.runCommand("/duel cancel " + challenge.ticket)));
    }

    public synchronized void answer(Player player, String action, String ticket) {
        var challenge = pending.get(player.getUniqueId());
        if (challenge == null || !challenge.ticket.toString().equals(ticket) || !clock.instant().isBefore(challenge.expires)) {
            player.sendMessage(messages.get("duel.expired"));
            return;
        }
        if (action.equals("cancel") && challenge.sender == player || action.equals("decline") && challenge.target == player) {
            cancel(challenge);
        } else if (action.equals("accept") && challenge.target == player && challenge.invited && !challenge.accepted
                && available(player) && reachable(challenge.sender, player) && here(challenge.sender).filter(challenge.server::equals).isPresent()) {
            challenge.accepted = true;
            challenge.expires = clock.instant().plusSeconds(settings.transferSeconds());
            var server = proxy.getServer(challenge.server);
            if (server.isEmpty() || directory.enter(player.getUniqueId(), challenge.server) != ServerDirectory.Entry.SEATED) {
                cancel(challenge);
                return;
            }
            send(challenge.sender, new ProxyMessage.DuelAccepted(challenge.ticket));
            transfer(challenge, player, server.get());
        }
    }

    public synchronized void cancel(Player sender, UUID ticket) {
        var challenge = pending.get(sender.getUniqueId());
        if (challenge != null && challenge.sender == sender && challenge.ticket.equals(ticket)) cancel(challenge);
    }

    private synchronized void cancel(Challenge challenge) {
        if (!pending.remove(challenge.sender.getUniqueId(), challenge)) return;
        pending.remove(challenge.target.getUniqueId(), challenge);
        challenge.cancelled = true;
        if (challenge.server != null && challenge.transfers == 0) directory.releaseDuel(challenge.server, challenge.players);
        for (var player : new Player[]{challenge.sender, challenge.target}) {
            if (player.isActive()) {
                player.sendMessage(messages.get("duel.cancelled"));
                returnToLobby(challenge, player);
            }
        }
    }

    private void returnToLobby(Challenge challenge, Player player) {
        if (player.isActive() && here(player).filter(server -> server.equals(challenge.server)).isPresent()) {
            if (player == challenge.sender) send(player, new ProxyMessage.DuelCancelled(challenge.ticket));
            lobby.send(player);
        }
    }

    public synchronized void tick() {
        cooldowns.values().removeIf(until -> !clock.instant().isBefore(until));
        for (var challenge : new HashSet<>(pending.values())) {
            if (!clock.instant().isBefore(challenge.expires) || !challenge.sender.isActive() || !challenge.target.isActive()
                    || !reachable(challenge.sender, challenge.target) || visibility.isVanished(challenge.sender) || visibility.isVanished(challenge.target)
                    || parties.isInParty(challenge.sender.getUniqueId()) || parties.isInParty(challenge.target.getUniqueId())
                    || ranked.isWaiting(challenge.sender.getUniqueId()) || ranked.isWaiting(challenge.target.getUniqueId())) {
                cancel(challenge);
            } else if (challenge.server == null) {
                if (available(challenge.sender) && available(challenge.target)) prepare(challenge);
                else cancel(challenge);
            }
        }
    }

    @Subscribe
    public synchronized void onConnect(ServerPostConnectEvent event) {
        var challenge = pending.get(event.getPlayer().getUniqueId());
        if (challenge == null || challenge.server == null) return;
        if (event.getPlayer() == challenge.sender && !challenge.opened && here(challenge.sender).filter(challenge.server::equals).isPresent()) {
            challenge.opened = true;
            send(challenge.sender, new ProxyMessage.DuelSetup(challenge.ticket, challenge.target.getUniqueId(), visibility.shownName(challenge.target)));
        } else if (event.getPlayer() == challenge.target && challenge.accepted && here(challenge.target).filter(challenge.server::equals).isPresent()) {
            pending.remove(challenge.sender.getUniqueId(), challenge);
            pending.remove(challenge.target.getUniqueId(), challenge);
            directory.releaseDuel(challenge.server, challenge.players);
        } else if (event.getPlayer() == challenge.sender || !here(challenge.target).filter(directory::isLobby).isPresent()) {
            cancel(challenge);
        }
    }

    @Subscribe
    public synchronized void onDisconnect(DisconnectEvent event) {
        if (LiveSession.endedBy(event)) {
            var challenge = pending.get(event.getPlayer().getUniqueId());
            if (challenge != null) cancel(challenge);
        }
    }

    private void send(Player player, ProxyMessage.ToBackend message) {
        player.getCurrentServer().ifPresent(connection -> connection.sendPluginMessage(CHANNEL, ProxyMessageCodec.encode(message)));
    }
}
