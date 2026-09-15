package me.blockcraft.system.party;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import me.blockcraft.system.PlayerChoices;
import me.blockcraft.system.PlayerVisibility;
import me.blockcraft.system.command.CommandSupport;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * In-memory parties keyed by UUID; a party follows its leader between servers and passes to the member who joined
 * first once the leader is gone.
 */
public final class PartyManager {

    private static final int MAX_MEMBERS = 6;
    private static final Duration INVITE_TTL = Duration.ofSeconds(60);
    private static final Duration JOIN_REQUEST_COOLDOWN = Duration.ofSeconds(30);

    private record Invite(UUID leader, Instant sentAt) {
    }

    private final ProxyServer proxy;
    private final PlayerVisibility visibility;
    private final PlayerChoices choices;
    private final Messages messages;
    private final Map<UUID, Party> byLeader = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> leaderByMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> joinRequests = new ConcurrentHashMap<>();

    public PartyManager(ProxyServer proxy, PlayerVisibility visibility, PlayerChoices choices, Messages messages) {
        this.proxy = proxy;
        this.visibility = visibility;
        this.choices = choices;
        this.messages = messages;
    }

    public synchronized boolean isInParty(UUID player) {
        return byLeader.containsKey(player) || leaderByMember.containsKey(player);
    }

    public synchronized boolean isLeader(UUID player) {
        return byLeader.containsKey(player);
    }

    public synchronized Optional<Party> partyOf(UUID player) {
        var own = byLeader.get(player);
        if (own != null) {
            return Optional.of(own);
        }
        var leader = leaderByMember.get(player);
        return leader == null ? Optional.empty() : Optional.ofNullable(byLeader.get(leader));
    }

    /** The names the members of that player's party are shown under, for tab completion. */
    public synchronized Stream<String> memberNames(UUID player) {
        return partyOf(player).stream()
                .flatMap(party -> party.members().stream())
                .flatMap(memberId -> proxy.getPlayer(memberId).stream())
                .map(visibility::shownName).toList().stream();
    }

    public synchronized void create(Player player) {
        if (isInParty(player.getUniqueId())) {
            player.sendMessage(messages.get("party.already-in-party"));
            return;
        }
        byLeader.put(player.getUniqueId(), new Party(player.getUniqueId()));
        player.sendMessage(messages.get("party.created"));
    }

    public synchronized void list(Player player) {
        var party = partyOf(player.getUniqueId());
        if (party.isEmpty()) {
            player.sendMessage(messages.get("party.not-in-party"));
            return;
        }
        proxy.getPlayer(party.get().leader()).ifPresentOrElse(
                leader -> player.sendMessage(messages.get("party.list-leader", name(leader), server(leader))),
                () -> player.sendMessage(messages.get("party.leader-offline")));
        player.sendMessage(messages.get("party.list-members"));
        for (UUID memberId : party.get().members()) {
            proxy.getPlayer(memberId).ifPresent(member ->
                    player.sendMessage(messages.get("party.list-member", name(member), server(member))));
        }
    }

    public synchronized void leave(Player player) {
        var party = partyOf(player.getUniqueId());
        if (party.isEmpty()) {
            player.sendMessage(messages.get("party.not-in-party"));
            return;
        }
        if (party.get().leader().equals(player.getUniqueId())) {
            disband(party.get(), messages.get("party.disbanded-by-leave"));
            player.sendMessage(messages.get("party.left-and-disbanded"));
            return;
        }
        party.get().members().remove(player.getUniqueId());
        leaderByMember.remove(player.getUniqueId());
        proxy.getPlayer(party.get().leader()).ifPresent(leader -> leader.sendMessage(messages.get("party.member-left", name(player))));
        player.sendMessage(messages.get("party.left"));
    }

    public synchronized void invite(Player inviter, Player invitee) {
        if (inviter.getUniqueId().equals(invitee.getUniqueId())) {
            inviter.sendMessage(messages.get("party.invite-self"));
            return;
        }
        if (!isInParty(inviter.getUniqueId())) {
            create(inviter);
        }
        var party = byLeader.get(inviter.getUniqueId());
        if (party == null) {
            inviter.sendMessage(messages.get("party.only-leader-invites"));
            return;
        }
        if (party.members().size() >= MAX_MEMBERS) {
            inviter.sendMessage(messages.get("party.full"));
            return;
        }
        if (isInParty(invitee.getUniqueId())) {
            inviter.sendMessage(messages.get("party.target-in-party"));
            return;
        }
        if (!choices.enabled(invitee, PlayerSetting.PARTY_INVITES)) {
            inviter.sendMessage(messages.get("party.invites-disabled", name(invitee)));
            return;
        }
        invites.put(invitee.getUniqueId(), new Invite(inviter.getUniqueId(), Instant.now()));
        inviter.sendMessage(messages.get("party.invited", name(invitee)));
        invitee.sendMessage(messages.get("party.invite-received", name(inviter)));
        invitee.sendMessage(messages.get("party.invite-accept", name(inviter))
                .clickEvent(ClickEvent.runCommand("/party accept"))
                .hoverEvent(HoverEvent.showText(messages.get("party.invite-accept-hover"))));
    }

    /** Only the leader can let someone in: the request is a clickable /party invite on the leader's screen. */
    public synchronized void requestInvite(Player player, Player leader) {
        var party = byLeader.get(leader.getUniqueId());
        if (party == null) {
            player.sendMessage(messages.get("party.target-not-leader"));
            return;
        }
        if (party.members().size() >= MAX_MEMBERS) {
            player.sendMessage(messages.get("party.full"));
            return;
        }
        var last = joinRequests.get(player.getUniqueId());
        if (last != null && Duration.between(last, Instant.now()).compareTo(JOIN_REQUEST_COOLDOWN) < 0) {
            player.sendMessage(messages.get("party.join-request-cooldown"));
            return;
        }
        joinRequests.put(player.getUniqueId(), Instant.now());
        player.sendMessage(messages.get("party.join-requested", name(leader)));
        leader.sendMessage(messages.get("party.join-request", name(player)));
        leader.sendMessage(messages.get("party.join-request-click", name(player))
                .clickEvent(ClickEvent.runCommand("/party invite " + visibility.shownName(player)))
                .hoverEvent(HoverEvent.showText(messages.get("party.join-request-hover", name(player)))));
    }

    public synchronized void accept(Player player) {
        if (isInParty(player.getUniqueId())) {
            player.sendMessage(messages.get("party.already-in-party"));
            return;
        }
        var invite = invites.remove(player.getUniqueId());
        if (invite == null) {
            player.sendMessage(messages.get("party.not-invited"));
            return;
        }
        var leader = proxy.getPlayer(invite.leader());
        var party = byLeader.get(invite.leader());
        if (leader.isEmpty() || party == null) {
            player.sendMessage(messages.get("party.leader-offline"));
            return;
        }
        if (Duration.between(invite.sentAt(), Instant.now()).compareTo(INVITE_TTL) > 0) {
            player.sendMessage(messages.get("party.invite-expired"));
            return;
        }
        if (party.members().size() >= MAX_MEMBERS) {
            player.sendMessage(messages.get("party.full"));
            return;
        }
        party.members().add(player.getUniqueId());
        leaderByMember.put(player.getUniqueId(), invite.leader());
        player.sendMessage(messages.get("party.joined", name(leader.get())));
        leader.get().sendMessage(messages.get("party.member-joined-leader", name(player)));
        broadcastToMembers(party, messages.get("party.member-joined", name(player)));
    }

    public synchronized void kick(Player leader, Player member) {
        var party = byLeader.get(leader.getUniqueId());
        if (party == null) {
            leader.sendMessage(messages.get("party.not-leader"));
            return;
        }
        if (!party.members().remove(member.getUniqueId())) {
            leader.sendMessage(messages.get("party.not-your-member"));
            return;
        }
        leaderByMember.remove(member.getUniqueId());
        member.sendMessage(messages.get("party.kicked", name(leader)));
        leader.sendMessage(messages.get("party.kicked-leader", name(member)));
        broadcastToMembers(party, messages.get("party.kicked-members", name(member)));
    }

    public synchronized void promote(Player leader, Player member) {
        var party = byLeader.get(leader.getUniqueId());
        if (party == null) {
            leader.sendMessage(messages.get("party.not-leader"));
            return;
        }
        if (member.getUniqueId().equals(leader.getUniqueId())) {
            leader.sendMessage(messages.get("party.promote-self"));
            return;
        }
        if (!party.members().contains(member.getUniqueId())) {
            leader.sendMessage(messages.get("party.not-your-member"));
            return;
        }
        var former = party.promote(member.getUniqueId());
        party.members().add(former);
        reindex(party, former);
        broadcast(party, messages.get("party.promoted", name(member)));
    }

    public synchronized void chat(Player player, String message) {
        var party = partyOf(player.getUniqueId());
        if (party.isEmpty()) {
            player.sendMessage(messages.get("party.not-in-party"));
            return;
        }
        var line = messages.get("party.chat", name(player),
                Placeholder.component("message", LegacyComponentSerializer.legacyAmpersand().deserialize(message)));
        proxy.getPlayer(party.get().leader()).ifPresent(leader -> leader.sendMessage(line));
        broadcastToMembers(party.get(), line);
    }

    public synchronized void followLeader(Player leader, RegisteredServer server) {
        var party = byLeader.get(leader.getUniqueId());
        if (party == null) {
            return;
        }
        var serverName = Placeholder.unparsed("server", server.getServerInfo().getName());
        leader.sendMessage(messages.get("party.leader-moved", serverName));
        for (UUID memberId : party.members()) {
            proxy.getPlayer(memberId)
                    .filter(member -> !member.getCurrentServer().map(current -> current.getServer().equals(server)).orElse(false))
                    .ifPresent(member -> {
                        member.sendMessage(messages.get("party.following", serverName));
                        member.createConnectionRequest(server).fireAndForget();
                    });
        }
    }

    public synchronized void onDisconnect(Player player) {
        joinRequests.remove(player.getUniqueId());
        invites.remove(player.getUniqueId());
        var own = byLeader.get(player.getUniqueId());
        if (own != null) {
            own.members().stream()
                    .flatMap(memberId -> proxy.getPlayer(memberId).stream())
                    .findFirst()
                    .ifPresentOrElse(next -> handOver(own, next),
                            () -> disband(own, messages.get("party.disbanded-by-disconnect")));
            return;
        }
        var leaderId = leaderByMember.remove(player.getUniqueId());
        if (leaderId == null) {
            return;
        }
        var party = byLeader.get(leaderId);
        if (party != null) {
            party.members().remove(player.getUniqueId());
        }
        proxy.getPlayer(leaderId).ifPresent(leader -> leader.sendMessage(messages.get("party.member-left", name(player))));
    }

    private void handOver(Party party, Player next) {
        reindex(party, party.promote(next.getUniqueId()));
        broadcast(party, messages.get("party.leader-handed-over", name(next)));
    }

    /** Both maps are keyed by the leader, so a hand-over has to write the new one into every entry. */
    private void reindex(Party party, UUID formerLeader) {
        byLeader.remove(formerLeader);
        invites.replaceAll((invitee, invite) -> invite.leader().equals(formerLeader)
                ? new Invite(party.leader(), invite.sentAt()) : invite);
        byLeader.put(party.leader(), party);
        leaderByMember.remove(party.leader());
        for (UUID memberId : party.members()) {
            leaderByMember.put(memberId, party.leader());
        }
    }

    private void disband(Party party, Component notice) {
        byLeader.remove(party.leader());
        invites.values().removeIf(invite -> invite.leader().equals(party.leader()));
        for (UUID memberId : party.members()) {
            leaderByMember.remove(memberId);
            proxy.getPlayer(memberId).ifPresent(member -> member.sendMessage(notice));
        }
    }

    private void broadcast(Party party, Component line) {
        proxy.getPlayer(party.leader()).ifPresent(leader -> leader.sendMessage(line));
        broadcastToMembers(party, line);
    }

    private void broadcastToMembers(Party party, Component line) {
        for (UUID memberId : party.members()) {
            proxy.getPlayer(memberId).ifPresent(member -> member.sendMessage(line));
        }
    }

    private TagResolver name(Player player) {
        return Placeholder.unparsed("player", visibility.shownName(player));
    }

    private static TagResolver server(Player player) {
        return Placeholder.unparsed("server", CommandSupport.currentServer(player));
    }
}
