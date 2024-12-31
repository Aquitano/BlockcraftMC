package de.Blockcraft.BungeeSystem.Listener;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MOTD.Config;
import de.Blockcraft.BungeeSystem.Main;
import de.Blockcraft.BungeeSystem.Util.BanManager;
import de.Blockcraft.BungeeSystem.Util.MuteManager;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerDisconnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.concurrent.TimeUnit;

public class PlayerLogin
        implements Listener {
    @EventHandler
    public void onLogin(LoginEvent e) {
        String playername = e.getConnection().getName();
        BanManager.createPlayer(playername);
        MuteManager.createPlayer(playername);
        if (BanManager.isBanned(playername)) {
            long current = System.currentTimeMillis();
            long end = BanManager.getEnd(playername);
            if (end > current || end == -1) {
                e.setCancelled(true);
                e.setCancelReason(BanManager.getBannedMessage(playername));
            } else {
                e.setCancelled(false);
                BanManager.unBan(playername, "Automatische Cloud");
            }
        }
    }

    @EventHandler
    public void on(ServerConnectEvent e) {
        try {
            for (ProxiedPlayer all : BungeeCord.getInstance().getPlayers()) {
                String header = "\u00a77\n \u00a77» \u00a7a" + Data.IP + " \u00a78\u2718 \u00a76Dein \u00a76Netzwerk \u00a77« \n \u00a76Derzeit spielen\u00a7a " + BungeeCord.getInstance().getOnlineCount() + "/" + Config.Config.MaxPlayers + " \u00a76Spieler \n \u00a77";
                String footer = "\u00a77\n \u00a7aServer \u00a78» \u00a76" + all.getServer().getInfo().getName() + " \u00a78\n \u00a7aDiscord \u00a78» \u00a76" + Data.DISCORD + "\u00a78 \n \u00a7aForum \u00a78\u00bb \u00a76" + Data.WEBSITE + " \n\u00a77";
                all.setTabHeader(new TextComponent(header), new TextComponent(footer));
            }
        } catch (Exception all) {
            // empty catch block
        }
    }

    @EventHandler
    public void on(ServerDisconnectEvent e) {
        try {
            for (ProxiedPlayer all : BungeeCord.getInstance().getPlayers()) {
                String header = "\u00a77\n \u00a77» \u00a7a" + Data.IP + " \u00a78\u2718 \u00a76Dein \u00a76Netzwerk \u00a77« \n \u00a76Derzeit spielen\u00a7a " + BungeeCord.getInstance().getOnlineCount() + "/" + Config.Config.MaxPlayers + " \u00a76Spieler \n \u00a77";
                String footer = "\u00a77\n \u00a7aServer \u00a78» \u00a76" + all.getServer().getInfo().getName() + " \u00a78\n \u00a7aDiscord \u00a78» \u00a76" + Data.DISCORD + "\u00a78 \n \u00a7aForum \u00a78» \u00a76" + Data.WEBSITE + " \n\u00a77";
                all.setTabHeader(new TextComponent(header), new TextComponent(footer));
            }
        } catch (Exception all) {
            // empty catch block
        }
    }

    @EventHandler
    public void on(ServerSwitchEvent e) {
        //String server = e.getPlayer().getServer().getInfo().getName();
        try {
            BungeeCord.getInstance().getScheduler().schedule(Main.instance, () -> {
                for (ProxiedPlayer all : BungeeCord.getInstance().getPlayers()) {
                    String header = "\u00a77\n \u00a77» \u00a7a" + Data.IP + " \u00a78\u2718 \u00a76Dein \u00a76Netzwerk \u00a77« \n \u00a76Derzeit spielen\u00a7a " + BungeeCord.getInstance().getOnlineCount() + "/" + Config.Config.MaxPlayers + " \u00a76Spieler \n \u00a77";
                    String footer = "\u00a77\n \u00a7aServer \u00a78» \u00a76" + all.getServer().getInfo().getName() + " \u00a78\n \u00a7aDiscord \u00a78» \u00a76" + Data.DISCORD + "\u00a78 \n \u00a7aForum \u00a78» \u00a76" + Data.WEBSITE + " \n\u00a77";
                    all.setTabHeader(new TextComponent(header), new TextComponent(footer));
                }
            }, 15, TimeUnit.MILLISECONDS);
        } catch (Exception var3_3) {
            // empty catch block
        }
    }

}

