package de.Blockcraft.Warns;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MOTD.Config;
import de.Blockcraft.BungeeSystem.MOTD.Manager_Chat;
import de.Blockcraft.BungeeSystem.Main;
import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.HubAPI.SelectLobby;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class ChatListener implements Listener {

    private static Boolean isMuted1(String playerName) {
        return Config.Config.MutedWerbung.contains(playerName);
    }

    @EventHandler
    public void onChat(ChatEvent e) {
        List<String> ads = Main.instance.getProhibitedAds();
        List<String> plugins = Main.instance.getProhibitedPlugins();
        List<String> wort = Main.instance.getProhibitedWords();
        ProxiedPlayer pl = (ProxiedPlayer) e.getSender();
        if (pl != null && !e.isCommand() && ChatListener.isMuted1(pl.getName())) {
            e.setCancelled(true);
            MessageUtil.sendPlayerMessage(pl, "\u00a77Der Chat wird dir untersagt. \u00a7cGrund: Du bist gemuted");
        }
        for (String p : plugins) {
            if (!e.getMessage().equals(p) || Objects.requireNonNull(pl).hasPermission("bs.plugins")) continue;
            MessageUtil.sendPlayerMessage(pl, "\u00a7cNicht verfügbar!");
            e.setCancelled(true);
        }
        for (String w : wort) {
            if (!e.getMessage().contains(w) || Objects.requireNonNull(pl).hasPermission("bs.wort")) continue;
            MessageUtil.sendPlayerMessage(pl, "\u00a77Diese Chatnachricht wurde vom System gefiltert. \u00a7cGrund: Anzügige Sprache");
            for (ProxiedPlayer p2 : BungeeCord.getInstance().getPlayers()) {
                if (!p2.hasPermission("bs.werbung.show")) continue;
                MessageListener.getInstance().sendMessage(p2, MessageType.INFO, new String[]{"\u00a77Der Spieler \u00a7c" + pl.getDisplayName() + "\u00a77 hat geschrieben: \u00a7c" + e.getMessage() + " \u00a77auf \u00a7c" + pl.getServer().getInfo().getName()});
            }
            e.setCancelled(true);
        }
        assert pl != null;
        if (!pl.hasPermission("bs.werbung.enable")) {
            final Pattern urlPattern = Pattern.compile("(https?://)?(www\\.)?\\S{2,}\\.(com|ru|net|org|co\\.uk|fr|tk|info|me|es|de|arpa|edu|firm|int|mil|mobi|nato|to|ms|vu|nl|ch|us|dk)", Pattern.CASE_INSENSITIVE);
            final Pattern ipPattern = Pattern.compile("(?<![0-9])((25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})[.,_ ]){3}(25[0-5]|2[0-4]\\d|[0-1]?\\d{1,2})(?![0-9])", Pattern.CASE_INSENSITIVE);

            boolean includesUrl = urlPattern.matcher(e.getMessage()).find() || ipPattern.matcher(e.getMessage()).find();
            for (String a : ads) {
                if (includesUrl)
                    break;

                if (!e.getMessage().contains(a) || e.getMessage().toLowerCase().contains("blockcraftmc.net"))
                    continue;

                includesUrl = true;
            }

            if (!includesUrl)
                return;

            for (ProxiedPlayer p2 : BungeeCord.getInstance().getPlayers()) {
                if (!p2.hasPermission("bs.werbung.show")) continue;
                MessageListener.getInstance().sendMessage(p2, MessageType.INFO, new String[]{"\u00a77Der Spieler \u00a7c" + pl.getDisplayName() + "\u00a77 hat geschrieben: \u00a7c" + e.getMessage() + " \u00a77auf \u00a7c" + pl.getServer().getInfo().getName()});
            }
            e.setCancelled(true);
            this.addWerbung(e.getMessage());
            Config.saveConfig();
            MessageListener.getInstance().sendMessage(pl, MessageType.BAD, new String[]{"\u00a77Deine ChatNachricht wurde gefiltert. \u00a7cGrund: Mögliche Werbung."});
            MessageListener.getInstance().sendMessage(pl, MessageType.BAD, new String[]{"\u00a7cDie Teammitglieder werden diesen Sachverhalt nun prüfen."});
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onJoin(ServerConnectEvent e) {
        ProxiedPlayer pl = e.getPlayer();
        if (pl != null && Manager_Chat.wartungen) {
            if (!pl.hasPermission("bs.maintenance.join")) {
                pl.disconnect(Config.Config.Prefix + "\u00a77Du kannst \u00a7c" + Data.IP + " \u00a77derzeit nicht betreten. \n \u00a77Derzeit sind \u00a7cWartungsarbeiten\u00a77.");
                e.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onKick(ServerKickEvent event) {
        if (!Objects.equals(event.getPlayer().getServer().getInfo().getName(), "lobby")) {
            if (!event.getKickReason().startsWith(".0")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Data.PREFIX + "\u00a77Deine \u00a7cVerbindung \u00a77zum Server \u00a7c" + event.getKickedFrom().getName() + "\u00a77 wurde getrennt. Grund: \u00a7c" + event.getKickReason());
            }
        } else {
            event.getPlayer().sendMessage(Data.PREFIX + "\u00a77Deine \u00a7cVerbindung \u00a77zum Server \u00a7c" + event.getKickedFrom().getName() + "\u00a77 wurde getrennt.");
        }
    }

    @EventHandler
    public void onTab(TabCompleteEvent e) {
        String Cursor = e.getCursor().toLowerCase();
        ProxiedPlayer p = (ProxiedPlayer) e.getSender();
        if (!p.hasPermission("bs.tab") && Cursor.startsWith("/")) {
            e.setCancelled(true);
        }
    }

    private void removeMute(String playerName) {
        ArrayList<String> removeList = new ArrayList<>();
        for (String s : Config.Config.MutedWerbung) {
            if (!s.equalsIgnoreCase(playerName)) continue;
            removeList.add(s);
        }
        for (String s2 : removeList) {
            Config.Config.MutedWerbung.remove(s2);
        }
    }

    private void addWerbung(String werbung) {
        this.removeMute(werbung);
        Config.Config.Werbungsserver.add(werbung);
    }

    @EventHandler
    public void on(ServerKickEvent e) {
        ServerInfo s1 = ProxyServer.getInstance().getServerInfo(SelectLobby.select());
        e.getPlayer().connect(s1);
    }
}

