package me.blockcraft.core;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HelpTeamCommand implements CommandExecutor {
    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        final Player p = (Player) sender;
        if (args.length == 1 && p.hasPermission("bs.team") && args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("");
            sender.sendMessage("\u00a78● \u00a79INFORMATIONEN \u00a76TEAM\u00a79 COMMANDS \u00a78●");
            sender.sendMessage("  \u00a7b/cc \u00a78- \u00a77Leert den Chat");
            sender.sendMessage("  \u00a7b/reports \u00a78- \u00a77Öffnet das ReportMenu");
            sender.sendMessage("  \u00a7b/food \u00a78- \u00a77Füllt deine Hungerbalken");
            sender.sendMessage("  \u00a7b/fly, /flyspeed, /speed");
            sender.sendMessage("  \u00a7b/heal");
            sender.sendMessage("  \u00a7b/invsee ,/sudo");
            sender.sendMessage("  \u00a7b/sethealth");
            sender.sendMessage("  \u00a7b/kill, /tp, /xp , /time");
            sender.sendMessage("  \u00a7b/rain, /sun");
            sender.sendMessage("  \u00a7b/msg \u00a78[Name] [MSG]");
            if (p.hasPermission("bs.team.admin")) {
                sender.sendMessage("");
                sender.sendMessage("   \u00a79mit /team help admin siehst du deine anderen Rechte!");
            }
            sender.sendMessage("");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help") && args[1].equalsIgnoreCase("admin") && p.hasPermission("bs.team.admin")) {
            sender.sendMessage("\u00a78● \u00a79INFORMATIONEN \u00a7fTEAM\u00a79 COMMANDS \u00a78●");
            sender.sendMessage("  \u00a7b/gm \u00a77[0-3]");
            sender.sendMessage("  \u00a7b/serverinfo");
            sender.sendMessage("  \u00a7b/item \u00a77[ID] [Anzahl]");
            sender.sendMessage("  \u00a7b/connect \u00a77[ServerName] ");
            sender.sendMessage("  \u00a7b/skull \u00a77[name]");
            sender.sendMessage("  \u00a7b/reflash");
            sender.sendMessage("  \u00a7b/enchant");
            sender.sendMessage("\u00a79EmeraldRush:");
            sender.sendMessage("  \u00a7b/rush endsuch");
            sender.sendMessage("  \u00a7b/rush endshop");
            sender.sendMessage("  \u00a7b/rush shop");
            sender.sendMessage("  \u00a7b/rushcoins set \u00a77[Name] [Emeralds]");
            sender.sendMessage("");
        }
        return false;
    }
}
