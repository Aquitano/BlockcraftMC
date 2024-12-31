package de.Blockcraft.BungeeSystem.StrikeSystem;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.BungeeSystem.Util.BanManager;
import de.Blockcraft.BungeeSystem.Util.Files;
import de.Blockcraft.BungeeSystem.Util.PlayerUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.config.Configuration;

public class StrikeCMD extends Command {
    private static final Configuration cfg = Files.MessagesConfiguration;
    private static final int MAX_STRIKES = 5;

    public StrikeCMD(String name) {
        super(name);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendPlayerMessage(sender, "\u00a7cFalsche Benutzung! \u00a76Nutze: \u00a73/strike [Spieler] [Grund]");
            return;
        }

        if (!sender.hasPermission("bs.strikesystem.del")) {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
            return;
        }

        String targetName = args[0];
        StringBuilder reasonBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            reasonBuilder.append(args[i]).append(" ");
        }
        String reason = reasonBuilder.toString().trim();

        ProxiedPlayer targetPlayer = ProxyServer.getInstance().getPlayer(targetName);

        if (targetPlayer == null) {
            MessageUtil.sendPlayerMessage(sender, "\u00a7cDer Spieler ist nicht online!");
            return;
        }

        String uuid = PlayerUtil.getUUID(targetName);
        if (uuid == null) {
            MessageUtil.sendPlayerMessage(sender, "\u00a7cUUID für den Spieler konnte nicht gefunden werden!");
            return;
        }

        if (!targetPlayer.hasPermission("bs.getstrike")) {
            MessageUtil.sendPlayerMessage(sender, "\u00a7cDu musst der Gruppe von \u00a76" + targetName + "\u00a7c die Permission geben: \u00a76bs.getstrike");
            return;
        }

        int currentStrikes = cfg.getInt("Players." + uuid + ".Strikes", 0);
        currentStrikes++;

        String strikeKey = "Players." + uuid + ".Grund" + currentStrikes;
        cfg.set(strikeKey, reason);

        cfg.set("Players." + uuid + ".Strikes", currentStrikes);

        Files.saveMessagesConfiguration();
        MessageUtil.sendPlayerMessage(targetPlayer, "\u00a7cDu hast einen \u00a76\u00a7lStrike \u00a7cerhalten! \u00a78[\u00a7b#" + currentStrikes + "\u00a78]");
        MessageUtil.sendPlayerMessage(sender, "\u00a76Das Teammitglied \u00a74" + targetName + "\u00a76 hat einen Strike erhalten!");

        if (currentStrikes >= MAX_STRIKES) {
            BanManager.ban(targetName, "Strike-Überschreitung", "StrikeSystem", -1);
            MessageUtil.sendPlayerMessage(sender, "\u00a73Dem Spieler \u00a76" + targetName + "\u00a73 wurde ein Strike entzogen \u00a74[#" + currentStrikes + "]");
            MessageUtil.sendPlayerMessage(targetPlayer, "\u00a73Dir wurde ein Strike entzogen! \u00a74[#" + currentStrikes + "]");
        }
    }
}
