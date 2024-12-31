package de.Blockcraft.Party;

import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.Blockcraft.Party.PartyCMD.PREFIX;

public class Events implements Listener {
    private final Logger logger = ProxyServer.getInstance().getLogger();

    public Events(Plugin main) {
        ProxyServer.getInstance().getPluginManager().registerListener(main, this);
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String playerName = player.getName();

        if (Party.partyLeaders.contains(playerName)) {
            ServerInfo newServer = player.getServer().getInfo();
            String serverName = newServer.getName();

            MessageUtil.sendPlayerMessage(player, PREFIX, ChatColor.GREEN + "Die Party hat den Server " + ChatColor.AQUA + serverName + ChatColor.GREEN + " betreten.");

            Set<String> partyMembers = Party.getPartyMembers(playerName);

            for (String memberName : partyMembers) {
                ProxiedPlayer member = ProxyServer.getInstance().getPlayer(memberName);
                if (member != null && !member.equals(player)) {
                    MessageUtil.sendPlayerMessage(member, PREFIX, ChatColor.GREEN + "Die Party betritt den Server " + ChatColor.AQUA + serverName + ChatColor.GREEN + ".");
                    member.connect(newServer);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String playerName = player.getName();

        if (Party.partyLeaders.contains(playerName)) {
            logger.log(Level.INFO, "Party leader {0} has disconnected. Disbanding the party.", playerName);
            Party.disbandParty(player);
        } else if (Party.playerPartyMap.containsKey(playerName)) {
            logger.log(Level.INFO, "Party member {0} has disconnected. Removing from the party.", playerName);
            Party.removeMember(player);
        }
    }
}