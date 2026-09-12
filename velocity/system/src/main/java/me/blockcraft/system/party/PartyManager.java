package me.blockcraft.system.party;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.blockcraft.common.message.Messages;
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

/** In-memory parties keyed by UUID; a party follows its leader between servers and dissolves when the leader leaves. */
public final class PartyManager {

    private static final int MAX_MEMBERS = 6;
    private static final Duration INVITE_TTL = Duration.ofSeconds(60);

    private record Invite(UUID leader, Instant sentAt) {
    }

    private final ProxyServer proxy;
    private final Messages messages;
    private final Map<UUID, Party> byLeader = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> leaderByMember = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();

    public PartyManager(ProxyServer proxy, Messages messages) {
        this.proxy = proxy;
        this.messages = messages;
    }

    public boolean isInParty(UUID player) {
        return byLeader.containsKey(player) || leaderByMember.containsKey(player);
    }

    public boolean isLeader(UUID player) {
        return byLeader.containsKey(player);
    }

    public Optional<Party> partyOf(UUID player) {
        var own = byLeader.get(player);
        if (own != null) {
            return Optional.of(own);
        }
        var leader = leaderByMember.get(player);
        return leader == null ? Optional.empty() : Optional.ofNullable(byLeader.get(leader));
    }

    public void create(Player player) {
        if (isInParty(player.getUniqueId())) {
            player.sendMessage(messages.get("party.already-in-party"));
            return;
        }
        byLeader.put(player.getUniqueId(), new Party(player.getUniqueId()));
        player.sendMessage(messages.get("party.created"));
    }

    public void list(Player player) {
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

    public void leave(Player player) {
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

    public void invite(Player inviter, Player invitee) {
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
        invites.put(invitee.getUniqueId(), new Invite(inviter.getUniqueId(), Instant.now()));
        inviter.sendMessage(messages.get("party.invited", name(invitee)));
        invitee.sendMessage(messages.get("party.invite-received", name(inviter)));
        invitee.sendMessage(messages.get("party.invite-accept", name(inviter))
                .clickEvent(ClickEvent.runCommand("/party accept"))
                .hoverEvent(HoverEvent.showText(messages.get("party.invite-accept-hover"))));
    }

    public void accept(Player player) {
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
        party.members().add(player.getUniqueId());
        leaderByMember.put(player.getUniqueId(), invite.leader());
        player.sendMessage(messages.get("party.joined", name(leader.get())));
        leader.get().sendMessage(messages.get("party.member-joined-leader", name(player)));
        broadcastToMembers(party, messages.get("party.member-joined", name(player)));
    }

    public void kick(Player leader, Player member) {
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

    public void chat(Player player, String message) {
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

    public void followLeader(Player leader, RegisteredServer server) {
        var party = byLeader.get(leader.getUniqueId());
        if (party == null) {
            return;
        }
        var serverName = Placeholder.unparsed("server", server.getServerInfo().getName());
        leader.sendMessage(messages.get("party.leader-moved", serverName));
        for (UUID memberId : party.members()) {
            proxy.getPlayer(memberId).ifPresent(member -> {
                member.sendMessage(messages.get("party.following", serverName));
                member.createConnectionRequest(server).fireAndForget();
            });
        }
    }

    public void onDisconnect(Player player) {
        var own = byLeader.get(player.getUniqueId());
        if (own != null) {
            disband(own, messages.get("party.disbanded-by-disconnect"));
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

    private void disband(Party party, Component notice) {
        byLeader.remove(party.leader());
        for (UUID memberId : party.members()) {
            leaderByMember.remove(memberId);
            proxy.getPlayer(memberId).ifPresent(member -> member.sendMessage(notice));
        }
    }

    private void broadcastToMembers(Party party, Component line) {
        for (UUID memberId : party.members()) {
            proxy.getPlayer(memberId).ifPresent(member -> member.sendMessage(line));
        }
    }

    private static TagResolver name(Player player) {
        return Placeholder.unparsed("player", player.getUsername());
    }

    private static TagResolver server(Player player) {
        return Placeholder.unparsed("server", CommandSupport.currentServer(player));
    }
}
