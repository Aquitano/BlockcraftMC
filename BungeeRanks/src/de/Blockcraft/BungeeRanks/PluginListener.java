package de.Blockcraft.BungeeRanks;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.io.*;

public class PluginListener
        implements Listener {
    @EventHandler
    public void onEvent(PluginMessageEvent event) {
        if (event.getTag().equalsIgnoreCase("BungeeCord")) {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()));
            try {
                String subchannel = in.readUTF();
                if (subchannel.equalsIgnoreCase("setChatPrefix")) {
                    event.setCancelled(true);
                    ProxiedPlayer p = this.getPlayer(in.readUTF());
                    if (p == null) {
                        return;
                    }
                    String Prefix = in.readUTF();
                    if (!Prefix.isEmpty()) {
                        PlayerListener.prefixes.put(p, Prefix);
                    } else {
                        PlayerListener.prefixes.remove(p);
                    }
                }
                if (subchannel.contains(" ")) {
                    String[] args = subchannel.split(" ");
                    if (args.length <= 1) {
                        return;
                    }
                    if (args[0].equalsIgnoreCase("isOP") && args.length == 3) {
                        event.setCancelled(true);
                        ProxiedPlayer p = this.getPlayer(args[1]);
                        if (p == null) {
                            return;
                        }
                        ByteArrayOutputStream b = new ByteArrayOutputStream();
                        DataOutputStream out = new DataOutputStream(b);
                        out.writeUTF("isOP");
                        out.writeUTF(p.getName());
                        out.writeUTF("true");
                        for (ServerInfo si : ProxyServer.getInstance().getServers().values()) {
                            if (si.getPlayers().isEmpty() || !si.getPlayers().contains(p)) continue;
                            si.sendData("BungeeCord", b.toByteArray());
                            return;
                        }
                    }
                }
            } catch (IOException subchannel) {
            }
        }
    }

    public ProxiedPlayer getPlayer(String PlayerName) {
        ProxiedPlayer pl = null;
        for (ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
            if (!p.getName().equalsIgnoreCase(PlayerName)) continue;
            pl = p;
        }
        return pl;
    }
}

