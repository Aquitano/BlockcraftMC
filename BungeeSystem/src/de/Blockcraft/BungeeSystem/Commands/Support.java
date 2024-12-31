package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Util.SupportManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Support extends Command {

    private static final String PREFIX = ChatColor.GRAY + "§ " + ChatColor.GOLD + "Support " + ChatColor.DARK_GRAY + "» " + ChatColor.RESET;

    private static final ConcurrentMap<String, String> supportRequests = new ConcurrentHashMap<>();

    public Support() {
        super("support");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Dieser Befehl kann nur von Spielern ausgeführt werden.");
            return;
        }

        if (args.length == 0) {
            handleSupportRequest(player);
        } else {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "accept":
                    handleAcceptCommand(player, args);
                    break;
                case "close":
                    handleCloseCommand(player, args);
                    break;
                default:
                    handleSendMessage(player, args);
                    break;
            }
        }
    }

    private void handleSupportRequest(ProxiedPlayer player) {
        String playerUUID = player.getUniqueId().toString();

        if (supportRequests.containsKey(playerUUID)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Du hast bereits einen offenen Supportantrag. Bitte warte, bis ein Supporter diesen bearbeitet.");
            return;
        }

        supportRequests.put(playerUUID, null); // No supporter assigned yet

        player.sendMessage(PREFIX + ChatColor.GREEN + "Du hast Support erfolgreich beantragt!");
        player.sendMessage(PREFIX + ChatColor.AQUA + "Verwende /support <Nachricht> um eine Nachricht an einen Supporter zu senden, sobald ein Supporter dem Chat beigetreten ist.");

        for (ProxiedPlayer staff : ProxyServer.getInstance().getPlayers()) {
            if (staff.hasPermission("bs.handlereports")) {
                TextComponent notifyMessage = new TextComponent(PREFIX + ChatColor.YELLOW + "Klicke " + ChatColor.AQUA + ChatColor.BOLD + "HIER" + ChatColor.YELLOW + " um den Supportantrag zu bearbeiten!");
                notifyMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(ChatColor.RED + "Klicke, um den Supportantrag zu bearbeiten.").create()));
                notifyMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/support accept " + player.getName()));

                staff.sendMessage(ChatColor.DARK_GREEN + "§m---------------------------------");
                staff.sendMessage(PREFIX + ChatColor.YELLOW + "Der Spieler " + ChatColor.GOLD + player.getName() + ChatColor.YELLOW + " hat Support beantragt.");
                staff.sendMessage(PREFIX + ChatColor.YELLOW + "Nutze " + ChatColor.GOLD + "/support accept " + player.getName() + ChatColor.YELLOW + " um den Supportantrag zu übernehmen.");
                staff.sendMessage(notifyMessage);
                staff.sendMessage(ChatColor.DARK_GREEN + "§m---------------------------------");
            }
        }
    }

    private void handleAcceptCommand(ProxiedPlayer staff, String[] args) {
        if (args.length != 2) {
            staff.sendMessage(PREFIX + ChatColor.RED + "Bitte verwende: /support accept <Spieler>");
            return;
        }

        String targetName = args[1];
        ProxiedPlayer target = ProxyServer.getInstance().getPlayer(targetName);

        if (target == null) {
            staff.sendMessage(PREFIX + ChatColor.RED + "Der Spieler " + ChatColor.GOLD + targetName + ChatColor.RED + " ist nicht online.");
            return;
        }

        String targetUUID = target.getUniqueId().toString();

        if (!supportRequests.containsKey(targetUUID)) {
            staff.sendMessage(PREFIX + ChatColor.RED + "Dieser Spieler hat keinen offenen Supportantrag.");
            return;
        }

        if (supportRequests.get(targetUUID) != null) {
            staff.sendMessage(PREFIX + ChatColor.RED + "Der Supportantrag des Spielers " + ChatColor.GOLD + targetName + ChatColor.RED + " wird bereits von einem anderen Supporter bearbeitet.");
            return;
        }

        supportRequests.put(targetUUID, staff.getUniqueId().toString());

        staff.sendMessage(PREFIX + ChatColor.GREEN + "Du bist nun im Support für " + ChatColor.GOLD + targetName + ChatColor.GREEN + ".");
        target.sendMessage(PREFIX + ChatColor.GREEN + staff.getName() + " hat deinen Supportantrag angenommen. Bitte schildere dein Anliegen mit /support <Nachricht>.");

        // Optionally, add the staff to a support session (handled by SupportManager)
        SupportManager.startSupportSession(target, staff);
    }

    private void handleCloseCommand(ProxiedPlayer staff, String[] args) {
        if (args.length != 2) {
            staff.sendMessage(PREFIX + ChatColor.RED + "Bitte verwende: /support close <Spieler>");
            return;
        }

        String targetName = args[1];
        ProxiedPlayer target = ProxyServer.getInstance().getPlayer(targetName);

        if (target == null) {
            staff.sendMessage(PREFIX + ChatColor.RED + "Der Spieler " + ChatColor.GOLD + targetName + ChatColor.RED + " ist nicht online.");
            return;
        }

        String targetUUID = target.getUniqueId().toString();

        if (!supportRequests.containsKey(targetUUID)) {
            staff.sendMessage(PREFIX + ChatColor.RED + "Dieser Spieler hat keinen offenen Supportantrag.");
            return;
        }

        if (!staff.getUniqueId().toString().equals(supportRequests.get(targetUUID))) {
            staff.sendMessage(PREFIX + ChatColor.RED + "Du bearbeitest derzeit keinen Supportantrag für diesen Spieler.");
            return;
        }

        supportRequests.remove(targetUUID);

        staff.sendMessage(PREFIX + ChatColor.GREEN + "Du hast den Support für " + ChatColor.GOLD + targetName + ChatColor.GREEN + " beendet.");
        target.sendMessage(PREFIX + ChatColor.GREEN + staff.getName() + " hat den Support beendet. Danke für deine Geduld.");

        SupportManager.endSupportSession(target, staff);
    }

    private void handleSendMessage(ProxiedPlayer player, String[] args) {
        if (supportRequests.containsKey(player.getUniqueId().toString())) {
            String supporterUUID = supportRequests.get(player.getUniqueId().toString());
            if (supporterUUID == null) {
                player.sendMessage(PREFIX + ChatColor.RED + "Bitte warte, bis ein Supporter deinen Antrag angenommen hat.");
                return;
            }

            ProxiedPlayer supporter = ProxyServer.getInstance().getPlayer(supporterUUID);
            if (supporter == null) {
                player.sendMessage(PREFIX + ChatColor.RED + "Der Supporter, der deinen Antrag angenommen hat, ist nicht mehr online.");
                supportRequests.remove(player.getUniqueId().toString());
                return;
            }

            String message = String.join(" ", args).trim();
            if (message.isEmpty()) {
                player.sendMessage(PREFIX + ChatColor.RED + "Bitte gib eine gültige Nachricht ein.");
                return;
            }

            String formattedMessage = ChatColor.RED + player.getName() + " : " + ChatColor.GRAY + message;
            supporter.sendMessage(formattedMessage);
            player.sendMessage(formattedMessage);
        } else {
            player.sendMessage(PREFIX + ChatColor.RED + "Du hast keinen aktiven Supportantrag. Verwende /support um einen zu erstellen.");
        }
    }
}
