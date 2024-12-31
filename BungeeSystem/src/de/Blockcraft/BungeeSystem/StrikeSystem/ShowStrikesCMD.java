package de.Blockcraft.BungeeSystem.StrikeSystem;

import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.BungeeSystem.Util.Files;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.config.Configuration;

public class ShowStrikesCMD extends Command {
    static final Configuration cfg = Files.MessagesConfiguration;

    public ShowStrikesCMD(String name) {
        super(name);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 1) {
            ProxiedPlayer p = ProxyServer.getInstance().getPlayer(args[0]);
            MessageUtil.sendPlayerMessage(sender, "\u00a74Die Strikes von " + p.getName());
            MessageUtil.sendPlayerMessage(sender, "\u00a73\u00a7m-----------------------------");
            MessageUtil.sendPlayerMessage(sender, "\u00a7eStrikes: \u00a74" + cfg.getInt(p.getUniqueId() + ".Strikes"));
            MessageUtil.sendPlayerMessage(sender, "\u00a7eGrund1: \u00a74" + cfg.getString(p.getUniqueId() + ".Grund1"));
            MessageUtil.sendPlayerMessage(sender, "\u00a7eGrund2: \u00a74" + cfg.getString(p.getUniqueId() + ".Grund2"));
            MessageUtil.sendPlayerMessage(sender, "\u00a7eGrund3: \u00a74" + cfg.getString(p.getUniqueId() + ".Grund3"));
            MessageUtil.sendPlayerMessage(sender, "\u00a7eGrund4: \u00a74" + cfg.getString(p.getUniqueId() + ".Grund4"));
            MessageUtil.sendPlayerMessage(sender, "\u00a7eGrund5: \u00a74" + cfg.getString(p.getUniqueId() + ".Grund5"));
            MessageUtil.sendPlayerMessage(sender, "\u00a73\u00a7m-----------------------------");
        } else {
            MessageUtil.sendPlayerMessage(sender, "\u00a7cBenutzung: \u00a76/showstrikes [Spieler]");
        }
    }
}
