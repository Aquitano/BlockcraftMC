package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class YouTuber
        extends Command {
    public YouTuber(String cmd) {
        super(cmd);
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer p)) {
            MessageUtil.sendPlayerMessage(sender, Data.PLAYER_ONLY);
            return;
        }

        if (args.length == 0) {
            sendDefaultMessage(p);
        } else if (args[0].equalsIgnoreCase("500")) {
            p.sendMessage("§8● §9Vorraussetzungen für §cYOUTUBER§8-§9RANG §8●");
            p.sendMessage("");
            p.sendMessage("\u00a78» \u00a77Dein \u00a7cKanal \u00a77muss mindestens \u00a7c500 Abonnenten\u00a77 besitzen.");
            p.sendMessage("\u00a78» \u00a77Du benötigst eine angemessene \u00a7cKlickzahl\u00a77.");
            p.sendMessage("\u00a78» \u00a77Ebenfalls musst du mindestens \u00a7c1 Video \u00a77aufnehmen.");
            p.sendMessage("\u00a78» \u00a77Dieses \u00a7cVideo \u00a77muss in angemessener \u00a7cQualität \u00a77und \u00a7cLänge \u00a77sein.");
            p.sendMessage("\u00a77Alles erfüllt? Beantrage den \u00a7cYouTuber \u00a77Rang im Forum \u00a7c" + Data.WEBSITE);
        } else if (args[0].equalsIgnoreCase("250")) {
            p.sendMessage("§8● §9Vorraussetzungen für §cPREMIUM+§8-§9RANG §8●");
            p.sendMessage("");
            p.sendMessage("\u00a78» \u00a77Dein \u00a7cKanal \u00a77muss mindestens \u00a7c250 Abonnenten\u00a77 besitzen.");
            p.sendMessage("\u00a78» \u00a77Du benötigst eine angemessene \u00a7cKlickzahl\u00a77.");
            p.sendMessage("\u00a78» \u00a77Ebenfalls musst du mindestens \u00a7c1 Video \u00a77aufnehmen.");
            p.sendMessage("\u00a78» \u00a77Dieses \u00a7cVideo \u00a77muss in angemessener \u00a7cQualität \u00a77und \u00a7cLänge \u00a77sein.");
            p.sendMessage("\u00a77Alles erfüllt? Beantrage den \u00a7cPremium+ \u00a77Rang im Forum \u00a7c" + Data.WEBSITE);
        } else {
            sendDefaultMessage(p);
        }
    }

    private void sendDefaultMessage(ProxiedPlayer p) {
        p.sendMessage("§8● §9INFORMATIONEN ZUM §5YOUTUBER§8-§9RANG §8●");
        p.sendMessage("");
        p.sendMessage("\u00a77Verwende \u00a7c/youtuber 250 \u00a77um die \u00a7cAnforderungen \u00a77für \u00a7cPremium+ \u00a77zu sehen.");
        p.sendMessage("\u00a77Verwende \u00a7c/youtuber 500 \u00a77um die \u00a7cAnforderungen \u00a77für \u00a7cYouTuber \u00a77zu sehen.");
    }
}

