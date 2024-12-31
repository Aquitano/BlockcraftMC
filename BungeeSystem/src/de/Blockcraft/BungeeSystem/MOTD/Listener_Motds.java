package de.Blockcraft.BungeeSystem.MOTD;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.HashMap;
import java.util.Random;

public class Listener_Motds
        implements Listener {
    public static final HashMap<String, String> playerData = new HashMap<>();
    static String newLine = System.lineSeparator();
    private static ServerPing.PlayerInfo[] onlineScore;

    public static void LoadMotds() {
        playerData.clear();
        if (newLine.length() == 2) {
            newLine = newLine.substring(1, 2);
        }
        onlineScore = Listener_Motds.getOnlinePlayers();
    }

    private static ServerPing.PlayerInfo[] getOnlinePlayers() {
        String s = Config.Config.DefaultMotd;
        while (s.startsWith(" ")) {
            s = s.substring(1);
        }
        while (s.endsWith(" ")) {
            s = s.substring(0, s.length() - 1);
        }
        ServerPing.PlayerInfo[] playerInfo2 = {new ServerPing.PlayerInfo(ChatColor.BLACK + " ", ""), new ServerPing.PlayerInfo(ChatColor.BLACK + "  " + ChatColor.WHITE + s.replace("&", "\u00a7") + ChatColor.BLACK + "  ", ""), new ServerPing.PlayerInfo(ChatColor.BLACK + " ", "")};
        return playerInfo2;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onEvent(ProxyPingEvent event) {
        ServerPing.Players onlinePlayers = new ServerPing.Players(Config.Config.MaxPlayers, ProxyServer.getInstance().getOnlineCount(), event.getResponse().getPlayers().getSample());
        if (event.getConnection().getVersion() == 4 || event.getConnection().getVersion() == 5) {
            onlinePlayers = new ServerPing.Players(Config.Config.MaxPlayers, ProxyServer.getInstance().getOnlineCount(), onlineScore);
        }
        String ServerImage = "";
        if (event.getResponse().getFavicon() != null) {
            ServerImage = event.getResponse().getFavicon();
        }
        if (Manager_Chat.wartungen) {
            event.setResponse(new ServerPing(new ServerPing.Protocol(ChatColor.RED + "Wartungsmodus " + ChatColor.GRAY + ProxyServer.getInstance().getOnlineCount() + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + Config.Config.MaxPlayers, 0), onlinePlayers, Config.Config.DefaultMotd.replace("&", "\u00a7") + newLine + ChatColor.DARK_RED + "\u00a77Der \u00a7cWartungsmodus\u00a77 ist derzeit aktiviert!", ServerImage));
            return;
        }
        ServerPing.Protocol version2 = new ServerPing.Protocol("\u00a74\u00a7lFalsche Version!", event.getResponse().getVersion().getProtocol());
        if (Config.Config.useMotds) {
            if (playerData.containsKey(event.getConnection().getAddress().getAddress().toString().replace("/", ""))) {
                String Message;
                if (Config.Config.RandomMotds.isEmpty()) {
                    Message = "";
                } else {
                    Random r = new Random();
                    Message = Config.Config.RandomMotds.get(r.nextInt(Config.Config.RandomMotds.size()));
                }
                event.setResponse(new ServerPing(version2, onlinePlayers, Config.Config.DefaultMotd.replace("&", "\u00a7") + ChatColor.RESET + newLine + ChatColor.RESET + Message.replace("&", "\u00a7").replace("%player%", playerData.get(event.getConnection().getAddress().getAddress().toString().replace("/", ""))), ServerImage));
            } else {
                int count = 0;
                String Message = "%player%";
                if (!Config.Config.RandomMotds.isEmpty()) {
                    while (count <= 10 && Message.contains("%player%")) {
                        Random r = new Random();
                        Message = Config.Config.RandomMotds.get(r.nextInt(Config.Config.RandomMotds.size()));
                        count = count + 1;
                    }
                }
                if (Message.contains("%player%")) {
                    Message = "";
                }
                event.setResponse(new ServerPing(version2, onlinePlayers, Config.Config.DefaultMotd.replace("&", "\u00a7") + ChatColor.RESET + newLine + ChatColor.RESET + Message.replace("&", "\u00a7"), ServerImage));
            }
        }
    }
}

