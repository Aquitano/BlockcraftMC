package de.Blockcraft.Party;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Party {
    static final Set<String> partyLeaders = ConcurrentHashMap.newKeySet();
    static final ConcurrentHashMap<String, String> playerPartyMap = new ConcurrentHashMap<>(); // PlayerName -> LeaderName
    private static final int MAX_PARTY_SIZE = 6;
    private static final String PREFIX = PartyCMD.PREFIX;
    private static final ConcurrentHashMap<String, Long> inviteTimeMap = new ConcurrentHashMap<>(); // PlayerName -> InviteTimestamp
    private static final ConcurrentHashMap<String, String> inviteMap = new ConcurrentHashMap<>(); // PlayerName -> LeaderName

    public static void createParty(ProxiedPlayer player) {
        String playerName = player.getName();

        if (isInParty(playerName)) {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.YELLOW + "Du bist bereits in einer Party.");
            return;
        }

        if (partyLeaders.contains(playerName)) {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.YELLOW + "Du bist bereits ein Party-Leader.");
            return;
        }

        partyLeaders.add(playerName);
        MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.GREEN + "Du hast eine neue Party erstellt.");
    }

    public static void listParty(ProxiedPlayer player) {
        String playerName = player.getName();

        if (partyLeaders.contains(playerName)) {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.GREEN + "Leader: " + ChatColor.GOLD + playerName + " " + ChatColor.GREEN + "- " + ChatColor.AQUA + player.getServer().getInfo().getName());
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.GREEN + "Mitglieder:");

            ProxyServer.getInstance().getPlayers().stream()
                    .filter(member -> playerName.equals(playerPartyMap.get(member.getName())))
                    .forEach(member -> MessageUtil.sendPlayerMessage(player,
                            PREFIX, ChatColor.GOLD + member.getName() + " " + ChatColor.GREEN + "- " + ChatColor.AQUA + member.getServer().getInfo().getName()));
        } else if (playerPartyMap.containsKey(playerName)) {
            String leaderName = playerPartyMap.get(playerName);
            ProxiedPlayer leader = ProxyServer.getInstance().getPlayer(leaderName);

            if (leader != null) {
                MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.GREEN + "Leader: " + ChatColor.GOLD + leaderName + " " + ChatColor.GREEN + "- " + ChatColor.AQUA + leader.getServer().getInfo().getName());
            } else {
                MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.RED + "Der Party-Leader ist nicht online.");
            }

            ProxyServer.getInstance().getPlayers().stream()
                    .filter(member -> leaderName.equals(playerPartyMap.get(member.getName())))
                    .forEach(member -> MessageUtil.sendPlayerMessage(player,
                            PREFIX, ChatColor.GOLD + member.getName() + " " + ChatColor.GREEN + "- " + ChatColor.AQUA + member.getServer().getInfo().getName()));
        } else {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.YELLOW + "Du bist in keiner Party.");
        }
    }

    public static void leaveParty(ProxiedPlayer player) {
        String playerName = player.getName();

        if (partyLeaders.contains(playerName)) {
            ProxyServer.getInstance().getPlayers().stream()
                    .filter(member -> playerName.equals(playerPartyMap.get(member.getName())))
                    .forEach(member -> {
                        playerPartyMap.remove(member.getName());
                        MessageUtil.sendPlayerMessage(member, PREFIX, ChatColor.RED + "Der Party-Leader hat die Party verlassen. Die Party wurde aufgelöst.");
                    });

            partyLeaders.remove(playerName);
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.RED + "Du hast die Party verlassen und sie wurde aufgelöst.");
        } else if (playerPartyMap.containsKey(playerName)) {
            String leaderName = playerPartyMap.get(playerName);
            ProxiedPlayer leader = ProxyServer.getInstance().getPlayer(leaderName);

            if (leader != null) {
                MessageUtil.sendPlayerMessage(leader, PREFIX, ChatColor.GOLD + playerName + " " + ChatColor.RED + "hat die Party verlassen.");
            }

            playerPartyMap.remove(playerName);
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.RED + "Du hast die Party verlassen.");
        } else {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.YELLOW + "Du bist in keiner Party.");
        }
    }

    public static void partyChat(ProxiedPlayer player, String message) {
        String playerName = player.getName();
        message = ChatColor.translateAlternateColorCodes('&', message);

        if (partyLeaders.contains(playerName)) {
            String formattedMessage = Data.PARTY_CHAT.replace("%player%", playerName).replace("%msg%", ChatColor.YELLOW + message);
            ProxyServer.getInstance().getPlayers().stream()
                    .filter(member -> playerName.equals(playerPartyMap.get(member.getName())))
                    .forEach(member -> MessageUtil.sendPlayerMessage(member, PREFIX, formattedMessage));
            MessageUtil.sendPlayerMessage(player, PREFIX, formattedMessage);
        } else if (playerPartyMap.containsKey(playerName)) {
            String leaderName = playerPartyMap.get(playerName);
            ProxiedPlayer leader = ProxyServer.getInstance().getPlayer(leaderName);

            if (leader != null) {
                String formattedMessage = Data.PARTY_CHAT.replace("%player%", playerName).replace("%msg%", ChatColor.YELLOW + message);
                MessageUtil.sendPlayerMessage(leader, PREFIX, formattedMessage);
                ProxyServer.getInstance().getPlayers().stream()
                        .filter(member -> leaderName.equals(playerPartyMap.get(member.getName())))
                        .forEach(member -> MessageUtil.sendPlayerMessage(member, PREFIX, formattedMessage));
            } else {
                MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.RED + "Der Party-Leader ist nicht online.");
            }
        } else {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.YELLOW + "Du bist in keiner Party.");
        }
    }

    public static void invitePlayer(ProxiedPlayer inviter, ProxiedPlayer invitee) {
        String inviterName = inviter.getName();
        String inviteeName = invitee.getName();
        long currentTime = System.currentTimeMillis();

        if (!partyLeaders.contains(inviterName) && !playerPartyMap.containsKey(inviterName)) {
            createParty(inviter);
            invitePlayer(inviter, invitee);
            return;
        }

        if (partyLeaders.contains(inviterName)) {
            long partySize = ProxyServer.getInstance().getPlayers().stream()
                    .filter(member -> inviterName.equals(playerPartyMap.get(member.getName())))
                    .count();

            if (partySize >= MAX_PARTY_SIZE) {
                MessageUtil.sendPlayerMessage(inviter, PREFIX, ChatColor.RED + "Die Party ist voll.");
                return;
            }

            if (playerPartyMap.containsKey(inviteeName) || partyLeaders.contains(inviteeName)) {
                MessageUtil.sendPlayerMessage(inviter, PREFIX, ChatColor.RED + "Dieser Spieler ist bereits in einer Party.");
                return;
            }

            inviteMap.put(inviteeName, inviterName);
            inviteTimeMap.put(inviteeName, currentTime);
            MessageUtil.sendPlayerMessage(inviter, PREFIX, ChatColor.GREEN + "Du hast " + ChatColor.GOLD + inviteeName + ChatColor.GREEN + " zur Party eingeladen.");
            MessageUtil.sendPlayerMessage(invitee, PREFIX, ChatColor.GOLD + inviterName + ChatColor.GREEN + " hat dich zur Party eingeladen.");

            TextComponent acceptMessage = new TextComponent(PREFIX + ChatColor.GREEN + "Betrete die Party mit " +
                    ChatColor.AQUA + "/party accept " + inviterName + ChatColor.GREEN + ".");
            acceptMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.YELLOW + "Klicke, um die Einladung anzunehmen.").create()));
            acceptMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept " + inviterName));
            invitee.sendMessage(acceptMessage);
        }
    }

    public static void acceptInvitation(ProxiedPlayer player) {
        String playerName = player.getName();

        if (partyLeaders.contains(playerName) || playerPartyMap.containsKey(playerName)) {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.YELLOW + "Du bist bereits in einer Party.");
            return;
        }

        if (!inviteMap.containsKey(playerName)) {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.YELLOW + "Du wurdest nicht eingeladen.");
            return;
        }

        String leaderName = inviteMap.get(playerName);
        ProxiedPlayer leader = ProxyServer.getInstance().getPlayer(leaderName);

        if (leader == null) {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.RED + "Der Party-Leader ist nicht online.");
            inviteMap.remove(playerName);
            inviteTimeMap.remove(playerName);
            return;
        }

        long currentTime = System.currentTimeMillis();
        long inviteTimestamp = inviteTimeMap.getOrDefault(playerName, 0L);
        long timeDifference = (currentTime - inviteTimestamp) / 1000; // in seconds

        if (timeDifference > 60) { // Invitation expires after 60 seconds
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.RED + "Diese Einladung ist abgelaufen.");
            inviteMap.remove(playerName);
            inviteTimeMap.remove(playerName);
            return;
        }

        playerPartyMap.put(playerName, leaderName);
        inviteMap.remove(playerName);
        inviteTimeMap.remove(playerName);

        MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.GREEN + "Du hast die Party von " + ChatColor.GOLD + leaderName + ChatColor.GREEN + " betreten.");
        MessageUtil.sendPlayerMessage(leader, PREFIX, ChatColor.GOLD + playerName + ChatColor.GREEN + " hat die Party betreten.");

        ProxyServer.getInstance().getPlayers().stream()
                .filter(member -> leaderName.equals(playerPartyMap.get(member.getName())))
                .forEach(member -> MessageUtil.sendPlayerMessage(member, PREFIX, ChatColor.GOLD + playerName + ChatColor.GREEN + " ist der Party beigetreten."));
    }

    public static void kickMember(ProxiedPlayer leader, ProxiedPlayer member) {
        String leaderName = leader.getName();
        String memberName = member.getName();

        if (!partyLeaders.contains(leaderName)) {
            MessageUtil.sendPlayerMessage(leader, PREFIX, ChatColor.YELLOW + "Du bist kein Party-Leader.");
            return;
        }

        if (!leaderName.equals(playerPartyMap.get(memberName)) && !partyLeaders.contains(memberName)) {
            MessageUtil.sendPlayerMessage(leader, PREFIX, ChatColor.YELLOW + "Dieser Spieler ist nicht in deiner Party.");
            return;
        }

        playerPartyMap.remove(memberName);
        MessageUtil.sendPlayerMessage(member, PREFIX, ChatColor.RED + "Du wurdest aus der Party von " + ChatColor.GOLD + leaderName + ChatColor.RED + " entfernt.");
        MessageUtil.sendPlayerMessage(leader, PREFIX, ChatColor.GREEN + "Du hast " + ChatColor.GOLD + memberName + ChatColor.GREEN + " aus der Party entfernt.");

        ProxyServer.getInstance().getPlayers().stream()
                .filter(p -> leaderName.equals(playerPartyMap.get(p.getName())))
                .forEach(p -> MessageUtil.sendPlayerMessage(p, PREFIX, ChatColor.GOLD + memberName + ChatColor.RED + " wurde aus der Party entfernt."));
    }

    public static void inviteMember(ProxiedPlayer player, ProxiedPlayer target) {
        String playerName = player.getName();
        String targetName = target.getName();
        long currentTime = System.currentTimeMillis();

        if (!partyLeaders.contains(playerName) && !playerPartyMap.containsKey(playerName)) {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.YELLOW + "Du bist nicht in einer Party.");
            return;
        }

        if (playerPartyMap.containsKey(targetName) || partyLeaders.contains(targetName)) {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.RED + "Dieser Spieler ist bereits in einer Party.");
            return;
        }

        long partySize = ProxyServer.getInstance().getPlayers().stream()
                .filter(p -> playerName.equals(playerPartyMap.get(p.getName())))
                .count();

        if (partyLeaders.contains(playerName)) {
            partySize += 1; // Including the leader
        }

        if (partySize >= MAX_PARTY_SIZE) {
            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.RED + "Die Party ist voll.");
            return;
        }

        inviteMap.put(targetName, playerName);
        inviteTimeMap.put(targetName, currentTime);

        MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.GREEN + "Du hast " + ChatColor.GOLD + targetName + ChatColor.GREEN + " zur Party eingeladen.");
        MessageUtil.sendPlayerMessage(target, PREFIX, ChatColor.GOLD + playerName + ChatColor.GREEN + " hat dich zur Party eingeladen.");

        TextComponent acceptMessage = new TextComponent(PREFIX + ChatColor.GREEN + "Betrete die Party mit " +
                ChatColor.AQUA + "/party accept " + playerName + ChatColor.GREEN + ".");
        acceptMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.YELLOW + "Klicke, um die Einladung anzunehmen.").create()));
        acceptMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept " + playerName));
        target.sendMessage(acceptMessage);
    }

    static boolean isInParty(String playerName) {
        return partyLeaders.contains(playerName) || playerPartyMap.containsKey(playerName);
    }

    public static Set<String> getPartyMembers(String playerName) {
        return ProxyServer.getInstance().getPlayers().stream()
                .map(ProxiedPlayer::getName)
                .filter(name -> playerName.equals(playerPartyMap.get(name)))
                .collect(java.util.stream.Collectors.toSet());
    }

    public static void disbandParty(ProxiedPlayer leader) {
        String leaderUUID = leader.getUniqueId().toString();
        Set<String> members = getPartyMembers(leaderUUID);

        for (String memberUUID : members) {
            ProxiedPlayer member = ProxyServer.getInstance().getPlayer(memberUUID);
            if (member != null) {
                MessageUtil.sendPlayerMessage(member, PREFIX, ChatColor.RED + "Die Party wurde aufgelöst, da der Leader die Verbindung getrennt hat.");
            }
            playerPartyMap.remove(memberUUID);
        }

        partyLeaders.remove(leaderUUID);
        MessageUtil.sendPlayerMessage(leader, PREFIX, ChatColor.RED + "Du hast die Party aufgelöst.");
    }

    public static void removeMember(ProxiedPlayer member) {
        String memberUUID = member.getUniqueId().toString();
        String leaderUUID = playerPartyMap.get(memberUUID);

        if (leaderUUID != null) {
            ProxiedPlayer leader = ProxyServer.getInstance().getPlayer(leaderUUID);
            if (leader != null) {
                MessageUtil.sendPlayerMessage(leader, PREFIX, ChatColor.YELLOW + member.getName() + " hat die Party verlassen.");
            }
            playerPartyMap.remove(memberUUID);
            MessageUtil.sendPlayerMessage(member, PREFIX, ChatColor.RED + "Du hast die Party verlassen.");
        }
    }

    public static boolean isPartyLeader(String string) {
        return partyLeaders.contains(string);
    }

    public static Object getPartyLeader(String string) {
        return playerPartyMap.get(string);
    }
}
