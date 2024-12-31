package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class Premium
        extends Command {
    public Premium(String cmd) {
        super(cmd);
    }

    @SuppressWarnings("deprecation")
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer p)) {
            MessageUtil.sendPlayerMessage(sender, Data.PLAYER_ONLY);
            return;
        }

        p.sendMessage("§8● §9INFORMATIONEN ZUM §6PREMIUM§8-§9RANG §8●");
        p.sendMessage("  §7Du möchtest den §6Premium§8-§7Rang kaufen und uns so unterstützen?");
        p.sendMessage("  §7Dann besuche unseren Shop: " + ChatColor.UNDERLINE + Data.SHOP);
        p.sendMessage("  §7Unsere Preise§8:");
        p.sendMessage("  §8» §f3 Monate§8: §710€");
        p.sendMessage("  §8» §fLifetime§8: §730€");
    }
}

