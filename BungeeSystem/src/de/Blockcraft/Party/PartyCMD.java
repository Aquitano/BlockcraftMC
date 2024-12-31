package de.Blockcraft.Party;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class PartyCMD extends Command {
    public static final String PREFIX = ChatColor.GOLD + "Party " + ChatColor.DARK_GRAY + "» " + ChatColor.RESET;

    public PartyCMD() {
        super("party");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return;
        }

        if (args.length < 1) {
            displayHelp(player);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                handleCreate(player);
                break;
            case "list":
                handleList(player);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "join":
                handleJoin(player, args);
                break;
            case "invite":
                handleInvite(player, args);
                break;
            case "accept":
                handleAccept(player);
                break;
            case "kick":
                handleKick(player, args);
                break;
            default:
                displayHelp(player);
                break;
        }
    }

    private void handleCreate(ProxiedPlayer player) {
        if (Party.isInParty(player.getUniqueId().toString())) {
            sendMessage(player, ChatColor.YELLOW + "Du bist bereits in einer Party.");
            return;
        }

        Party.createParty(player);
    }

    private void handleList(ProxiedPlayer player) {
        Party.listParty(player);
    }

    private void handleLeave(ProxiedPlayer player) {
        Party.leaveParty(player);
    }

    private void handleJoin(ProxiedPlayer player, String[] args) {
        if (args.length < 2) {
            sendMessage(player, ChatColor.YELLOW + "Verwende: " + ChatColor.AQUA + "/party join <Leader>");
            return;
        }

        String leaderName = args[1];
        ProxiedPlayer leader = ProxyServer.getInstance().getPlayer(leaderName);

        if (leader == null) {
            sendMessage(player, ChatColor.RED + "Dieser Spieler ist nicht online.");
            return;
        }

        if (!Party.partyLeaders.contains(leader.getUniqueId().toString())) {
            sendMessage(player, ChatColor.RED + "Dieser Spieler ist kein Party-Leader.");
            return;
        }

        if (Party.isInParty(player.getUniqueId().toString())) {
            sendMessage(player, ChatColor.YELLOW + "Du bist bereits in einer Party.");
            return;
        }

        Party.inviteMember(leader, player);
    }

    private void handleInvite(ProxiedPlayer inviter, String[] args) {
        if (!inviter.hasPermission("bs.party.invite")) {
            sendMessage(inviter, ChatColor.RED + "Du hast keine Berechtigung, Spieler einzuladen.");
            return;
        }

        if (args.length < 2) {
            sendMessage(inviter, ChatColor.YELLOW + "Verwende: " + ChatColor.AQUA + "/party invite <Player>");
            return;
        }

        String inviteeName = args[1];
        ProxiedPlayer invitee = ProxyServer.getInstance().getPlayer(inviteeName);

        if (invitee == null) {
            sendMessage(inviter, ChatColor.RED + "Dieser Spieler ist nicht online.");
            return;
        }

        if (Party.isInParty(invitee.getUniqueId().toString())) {
            sendMessage(inviter, ChatColor.RED + "Dieser Spieler ist bereits in einer Party.");
            return;
        }

        if (Party.isInParty(inviter.getUniqueId().toString()) && !Party.partyLeaders.contains(inviter.getUniqueId().toString())) {
            sendMessage(inviter, ChatColor.RED + "Nur Party-Leader können Spieler einladen.");
            return;
        }

        Party.invitePlayer(inviter, invitee);
    }

    private void handleAccept(ProxiedPlayer player) {
        Party.acceptInvitation(player);
    }

    private void handleKick(ProxiedPlayer leader, String[] args) {
        if (!leader.hasPermission("bs.party.kick")) {
            sendMessage(leader, ChatColor.RED + "Du hast keine Berechtigung, Spieler zu kicken.");
            return;
        }

        if (args.length < 2) {
            sendMessage(leader, ChatColor.YELLOW + "Verwende: " + ChatColor.AQUA + "/party kick <Player>");
            return;
        }

        String memberName = args[1];
        ProxiedPlayer member = ProxyServer.getInstance().getPlayer(memberName);

        if (member == null) {
            sendMessage(leader, ChatColor.RED + "Dieser Spieler ist nicht online.");
            return;
        }

        if (!Party.isPartyLeader(leader.getUniqueId().toString())) {
            sendMessage(leader, ChatColor.RED + "Nur Party-Leader können Spieler kicken.");
            return;
        }

        if (!Party.isInParty(member.getUniqueId().toString()) || !Party.getPartyLeader(member.getUniqueId().toString()).equals(leader.getUniqueId().toString())) {
            sendMessage(leader, ChatColor.RED + "Dieser Spieler ist nicht in deiner Party.");
            return;
        }

        Party.kickMember(leader, member);
    }

    private void displayHelp(ProxiedPlayer player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8● &9INFORMATIONEN ZUR PARTY &8●\n"));
        sendMessage(player, ChatColor.YELLOW + "Zeige diese Liste: " + ChatColor.AQUA + "/party help");
        sendMessage(player, ChatColor.YELLOW + "Lade Spieler ein: " + ChatColor.AQUA + "/party invite <Player>");
        sendMessage(player, ChatColor.YELLOW + "Zeige alle Member: " + ChatColor.AQUA + "/party list");
        sendMessage(player, ChatColor.YELLOW + "Verlasse die Party: " + ChatColor.AQUA + "/party leave");
        sendMessage(player, ChatColor.YELLOW + "Nehme Einladungen an: " + ChatColor.AQUA + "/party accept");
        sendMessage(player, ChatColor.YELLOW + "Kicke einen Spieler: " + ChatColor.AQUA + "/party kick <Player>");
        sendMessage(player, ChatColor.YELLOW + "Springe auf den Server: " + ChatColor.AQUA + "/party join <Leader>");
        sendMessage(player, ChatColor.YELLOW + "Party-Chat: " + ChatColor.AQUA + "/pc <Message>");
    }

    private void sendMessage(ProxiedPlayer player, String message) {
        player.sendMessage(PREFIX + message);
    }
}