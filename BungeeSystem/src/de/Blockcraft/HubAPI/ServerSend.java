package de.Blockcraft.HubAPI;

import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class ServerSend {
    public static void send(ProxiedPlayer p, ServerInfo destination) {
        try {
            p.connect(destination);
        } catch (Exception e) {
            MessageUtil.sendPlayerMessage(p, "§cDer Server ist nicht verfügbar.");
            e.printStackTrace();
        }
    }
}

