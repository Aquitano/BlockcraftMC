package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MOTD.Manager_Chat;
import de.Blockcraft.BungeeSystem.Main;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.concurrent.TimeUnit;

public class Wartungen
        extends Command {
    public Wartungen() {
        super("wartungen");
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender arg0, String[] arg1) {
        ProxiedPlayer p = (ProxiedPlayer) arg0;
        if (p.hasPermission("bs.maintenance")) {
            if (!Manager_Chat.wartungen) {
                if (arg1.length == 0) {
                    BungeeCord.getInstance().broadcast(Data.PREFIX + "\u00a77Das \u00a7c" + Data.IP + " Netzwerk \u00a77wird in \u00a7ceiner Minute \u00a77in den \u00a7cWartungsmodus \u00a77versetzt.");
                    ProxyServer.getInstance().getScheduler().schedule(Main.instance, () -> BungeeCord.getInstance().broadcast(Data.PREFIX + "\u00a77Das \u00a7c" + Data.IP + " Netzwerk \u00a77wird in \u00a7c30 Sekunden \u00a77in den \u00a7cWartungsmodus \u00a77versetzt."), 30, TimeUnit.SECONDS);
                    ProxyServer.getInstance().getScheduler().schedule(Main.instance, () -> BungeeCord.getInstance().broadcast(Data.PREFIX + "\u00a77Das \u00a7c" + Data.IP + " Netzwerk \u00a77wird in \u00a7c10 Sekunden \u00a77in den \u00a7cWartungsmodus \u00a77versetzt."), 50, TimeUnit.SECONDS);
                    ProxyServer.getInstance().getScheduler().schedule(Main.instance, () -> BungeeCord.getInstance().broadcast(Data.PREFIX + "\u00a77Das \u00a7c" + Data.IP + " Netzwerk \u00a77wird in \u00a7c5 Sekunden \u00a77in den \u00a7cWartungsmodus \u00a77versetzt."), 55, TimeUnit.SECONDS);
                    ProxyServer.getInstance().getScheduler().schedule(Main.instance, () -> {
                        Manager_Chat.wartungen = true;
                        ProxyServer.getInstance().broadcast(Data.PREFIX + "\u00a77Das \u00a7c" + Data.IP + " Netzwerk \u00a77befindet sich \u00a7cnun \u00a77im \u00a7cWartungsmodus\u00a77.");
                        for (ProxiedPlayer p2 : ProxyServer.getInstance().getPlayers()) {
                            if (p2.hasPermission("bs.maintenance.join")) continue;
                            p2.disconnect(Data.PREFIX + "\u00a77Das \u00a7c" + Data.IP + " Netzwerk \u00a77bfeindet sich nun im \u00a7cWartungsmodus. \n \u00a77Das \u00a7cBetreten \u00a77des \u00a7cNetzwerkes\u00a77 ist derzeit nicht möglich.");
                        }
                    }, 60, TimeUnit.SECONDS);
                } else if (arg1.length == 1 && arg1[0].equalsIgnoreCase("now")) {
                    Manager_Chat.wartungen = true;
                    ProxyServer.getInstance().broadcast(Data.PREFIX + "\u00a76Wartungsmodus wurde aktiviert.");
                    for (ProxiedPlayer p1 : ProxyServer.getInstance().getPlayers()) {
                        if (p1.hasPermission("bs.maintenance.join")) continue;
                        p1.disconnect(Data.PREFIX + "\u00a77Das \u00a7c" + Data.IP + " Netzwerk \u00a77bfeindet sich nun im \u00a7cWartungsmodus. \n \u00a77Das \u00a7cBetreten \u00a77des \u00a7cNetzwerkes\u00a77 ist derzeit nicht möglich.");
                    }
                }
            } else {
                Manager_Chat.wartungen = false;
                ProxyServer.getInstance().broadcast(Data.PREFIX + "\u00a76Wartungsmodus wurde deaktiviert.");
            }
        } else {
            MessageUtil.sendPlayerMessage(p, Data.NO_PERM);
        }
    }

}

