package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.Main;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.concurrent.TimeUnit;

public class Restart
        extends Command {
    public Restart() {
        super("restart");
    }

    public void execute(CommandSender arg0, String[] arg1) {
        ProxiedPlayer p = (ProxiedPlayer) arg0;
        if (p.hasPermission("bs.restart")) {
            BungeeCord.getInstance().broadcast(Data.PREFIX + "\u00a77Das \u00a7c" + Data.IP + " Netzwerk \u00a77startet in \u00a7ceiner Minute \u00a77neu.");
            ProxyServer.getInstance().getScheduler().schedule(Main.instance, () -> BungeeCord.getInstance().broadcast(Data.PREFIX + "\u00a77Das \u00a7c" + Data.IP + " Netzwerk \u00a77startet in \u00a7c30 Sekunden \u00a77neu."), 30, TimeUnit.SECONDS);
            ProxyServer.getInstance().getScheduler().schedule(Main.instance, () -> BungeeCord.getInstance().broadcast(Data.PREFIX + "\u00a77Das \u00a7c" + Data.IP + " Netzwerk \u00a77startet in \u00a7c10 Sekunden \u00a77neu."), 50, TimeUnit.SECONDS);
            ProxyServer.getInstance().getScheduler().schedule(Main.instance, () -> BungeeCord.getInstance().broadcast(Data.PREFIX + "\u00a77Das \u00a7c" + Data.IP + " Netzwerk \u00a77startet in \u00a7c5 Sekunden \u00a77neu."), 55, TimeUnit.SECONDS);
            ProxyServer.getInstance().getScheduler().schedule(Main.instance, () -> {
                for (ProxiedPlayer p1 : ProxyServer.getInstance().getPlayers()) {
                    p1.disconnect(Data.PREFIX + "\u00a77Der Server restartet.");
                }
            }, 60, TimeUnit.SECONDS);
            ProxyServer.getInstance().getScheduler().schedule(Main.instance, () -> ProxyServer.getInstance().stop(), 61, TimeUnit.SECONDS);
        }
    }
}



