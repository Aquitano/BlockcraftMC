package de.Blockcraft.BungeeSystem.StrikeSystem;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MessageUtil;
import de.Blockcraft.BungeeSystem.Util.Files;
import de.Blockcraft.BungeeSystem.Util.PlayerUtil;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.config.Configuration;

public class DelStrikeCMD extends Command {
    private static final Configuration cfg = Files.MessagesConfiguration;

    public DelStrikeCMD(String name) {
        super(name);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            MessageUtil.sendPlayerMessage(sender, "\u00a7cBenutzung: \u00a76/delstrike [Spieler]");
            return;
        }

        String targetName = args[0];
        ProxiedPlayer targetPlayer = ProxyServer.getInstance().getPlayer(targetName);

        if (!sender.hasPermission("bs.strikesystem.del")) {
            MessageUtil.sendPlayerMessage(sender, Data.NO_PERM);
            return;
        }

        if (targetPlayer == null) {
            MessageUtil.sendPlayerMessage(sender, "\u00a7cSpieler nicht gefunden!");
            return;
        }

        String uuid = PlayerUtil.getUUID(targetName);
        if (uuid == null) {
            MessageUtil.sendPlayerMessage(sender, "\u00a7cUUID für den Spieler konnte nicht gefunden werden!");
            return;
        }

        int strikes = cfg.getInt("Players." + uuid + ".Strikes", 0);
        if (strikes == 0) {
            MessageUtil.sendPlayerMessage(sender, "\u00a7cDer Spieler hat keine Strikes!");
            return;
        }

        // Remove the highest strike
        removeStrike(uuid, strikes);

        // Notify sender and the player
        if (strikes == 1) {
            MessageUtil.sendPlayerMessage(sender, "\u00a73Dem Spieler \u00a76" + targetName + "\u00a73 wurde ein Strike entzogen \u00a74[#1]");
            MessageUtil.sendPlayerMessage(targetPlayer, "\u00a73Dir wurde ein Strike entzogen! \u00a74[#1]");
        } else {
            MessageUtil.sendPlayerMessage(sender, "\u00a73Dem Spieler \u00a76" + targetName + "\u00a73 wurde ein Strike entzogen \u00a74[#" + strikes + "]");
            MessageUtil.sendPlayerMessage(targetPlayer, "\u00a73Dir wurde ein Strike entzogen! \u00a74[#"+ strikes + "]");
        }

        // Update the remaining strikes
        strikes = cfg.getInt("Players." + uuid + ".Strikes", 0);
        MessageUtil.sendPlayerMessage(sender, "\u00a77Verbleibende Strikes: \u00a76" + strikes);
        MessageUtil.sendPlayerMessage(targetPlayer, "\u00a77Verbleibende Strikes: \u00a76" + strikes);
    }

    private void removeStrike(String uuid, int currentStrikes) {
        // Shift Grund entries down
        for (int i = currentStrikes; i >= 1; i--) {
            String currentGrundPath = "Players." + uuid + ".Grund" + i;
            String previousGrundPath = "Players." + uuid + ".Grund" + (i - 1);

            if (i == 1) {
                cfg.set(currentGrundPath, null);
            } else {
                String previousGrund = cfg.getString(previousGrundPath, "");
                cfg.set(currentGrundPath, previousGrund.isEmpty() ? null : previousGrund);
                cfg.set(previousGrundPath, null);
            }
        }

        // Decrement Strikes
        if (currentStrikes > 1) {
            cfg.set("Players." + uuid + ".Strikes", currentStrikes - 1);
        } else {
            cfg.set("Players." + uuid + ".Strikes", null);
        }

        // Save configuration
        Files.saveMessagesConfiguration();
    }
}