package de.Blockcraft.HubAPI;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class HubCMD
        extends Command {
    public HubCMD(String cmd) {
        super(cmd);
    }

    public void execute(CommandSender arg0, String[] arg1) {
        ProxiedPlayer p = (ProxiedPlayer) arg0;
        Hub.send(p);
    }
}

