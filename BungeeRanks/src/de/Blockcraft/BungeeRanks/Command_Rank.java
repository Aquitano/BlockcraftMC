package de.Blockcraft.BungeeRanks;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.List;
import java.util.Objects;

public class Command_Rank extends Command {
    private static final String PREFIX_SERVER = "§8[§9BlockcraftAPI§8] §7";
    private static final String PREFIX_INGAME = "§7§ §6Blockcraft §8§ §7";
    public static boolean isGlobalMuted;

    public Command_Rank(String name) {
        super(name);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (isPlayerWithoutPermission(sender, "bs.rank.*")) {
            sendMessage(sender, ChatColor.RED, "Du hast keine Berechtigungen, um diesen Befehl auszuführen!");
            return;
        }

        if (args.length == 0) {
            showHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                showHelp(sender);
                break;
            case "mute":
                toggleGlobalMute(sender);
                break;
            case "set":
                handleSetCommand(sender, args);
                break;
            case "get":
                handleGetCommand(sender, args);
                break;
            case "list":
                handleListCommand(sender, args);
                break;
            case "group":
                handleGroupCommand(sender, args);
                break;
            case "perm":
                handlePermCommand(sender, args);
                break;
            default:
                sendMessage(sender, ChatColor.RED, "Unbekannter Unterbefehl. Benutze /rank help für eine Liste der Befehle.");
                break;
        }
    }

    private boolean isPlayerWithoutPermission(CommandSender sender, String permission) {
        return sender instanceof ProxiedPlayer && !sender.hasPermission(permission);
    }

    private void toggleGlobalMute(CommandSender sender) {
        isGlobalMuted = !isGlobalMuted;
        String status = isGlobalMuted ? "aktiviert" : "deaktiviert";
        sendMessage(sender, ChatColor.GOLD, "Du hast Globalmute " + status + ".");

        String broadcastMessage = "§8[§6System§8] §3Globalmute wurde nun §e" + status + "§3!";
        ProxyServer.getInstance().getPlayers().forEach(player -> player.sendMessage(broadcastMessage));
    }

    private void handleSetCommand(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendMessage(sender, ChatColor.RED, "/rank set <player> <rank>");
            return;
        }

        String playerName = BungeeRanks.playerExists(args[1]);
        if (playerName == null) {
            sendMessage(sender, ChatColor.RED, "Der Spieler wurde nicht gefunden!");
            return;
        }

        String rank = args[2];
        String uuid = BungeeRanks.getUUID(playerName);
        System.out.println(uuid);

        if (BungeeRanks.setRank(playerName, rank, uuid)) {
            sendMessage(sender, ChatColor.GREEN, "Der Spieler \"" + ChatColor.RED + playerName + ChatColor.GREEN +
                    "\" hat jetzt den Rang \"" + ChatColor.RED + BungeeRanks.getRankDisplayName(rank) + ChatColor.GREEN + "\".");

            ProxiedPlayer targetPlayer = ProxyServer.getInstance().getPlayer(playerName);
            if (targetPlayer != null) {
                sendMessage(targetPlayer, ChatColor.RED, "Du" + ChatColor.GOLD + " hast jetzt den Rang \"" +
                        ChatColor.RED + BungeeRanks.getRankDisplayName(rank) + ChatColor.GOLD + "\".");
            }
        } else {
            sendMessage(sender, ChatColor.RED, "Der Rang \"" + rank + "\" wurde nicht gefunden!");
        }
    }

    private void handleGetCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> ranks = BungeeRanks.getPlayerRanks();
            sendMessage(sender, ChatColor.GOLD, "Ränge der Spieler (" + ranks.size() + "):");
            ranks.forEach(rank -> sendMessage(sender, ChatColor.GRAY, "  " + rank));
            return;
        }

        if (args.length == 2) {
            String playerName = BungeeRanks.playerExists(args[1]);
            if (playerName == null) {
                sendMessage(sender, ChatColor.GOLD, "Der Spieler " + ChatColor.RED + args[1] + ChatColor.GOLD +
                        " hat den Rang \"" + ChatColor.RED + BungeeRanks.getDefaultRankName() + ChatColor.GOLD + "\".");
                return;
            }

            String rankName = BungeeRanks.getRankName(playerName);
            if (rankName == null) {
                sendMessage(sender, ChatColor.GOLD, "Der Spieler " + ChatColor.RED + args[1] + ChatColor.GOLD +
                        " hat den Rang \"" + ChatColor.RED + BungeeRanks.getDefaultRankName() + ChatColor.GOLD + "\".");
                return;
            }

            sendMessage(sender, ChatColor.GOLD, "Der Spieler " + ChatColor.RED + playerName + ChatColor.GOLD +
                    " hat den Rang \"" + ChatColor.RED + rankName + ChatColor.GOLD + "\".");
            return;
        }

        sendMessage(sender, ChatColor.RED, "/rank get [player]");
    }

    private void handleListCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            sendMessage(sender, ChatColor.GOLD, "Ränge: " + ChatColor.GRAY);
            BungeeRanks.getAllRanks().forEach(rank -> sendMessage(sender, ChatColor.GRAY, "  " + ChatColor.YELLOW + rank));
        } else if (args.length == 2) {
            String rankName = BungeeRanks.getRankDisplayName(args[1]);
            if (rankName == null) {
                sendMessage(sender, ChatColor.RED, "Die Gruppe wurde nicht gefunden!");
                return;
            }
            sendMessage(sender, ChatColor.GOLD, "Berechtigungen für die Gruppe \"" + ChatColor.YELLOW + rankName + ChatColor.GOLD + "\":");
            BungeeRanks.getAllRanks().stream()
                    .filter(rank -> rank.equalsIgnoreCase(rankName))
                    .forEach(rank -> {
                        sendMessage(sender, ChatColor.GRAY, "    Prefix: " + BungeeRanks.getRankPrefix(rank)
                                + ChatColor.GRAY + " (" + Objects.requireNonNull(BungeeRanks.getRankPrefix(rank)).replace("&", "§") + ChatColor.GRAY + ")");
                        boolean isDefault = BungeeRanks.getDefaultRankName().equalsIgnoreCase(rank);
                        sendMessage(sender, ChatColor.GRAY, "    Default: " + isDefault);
                        sendMessage(sender, ChatColor.GRAY, "    Permissions:");
                        for (String perm : Objects.requireNonNull(BungeeRanks.getRankPermissions(rank))) {
                            if (perm.startsWith("-")) {
                                sendMessage(sender, ChatColor.GRAY, "    - " + ChatColor.RED + perm.toLowerCase());
                            } else {
                                sendMessage(sender, ChatColor.GRAY, "    - " + ChatColor.GREEN + perm.toLowerCase());
                            }
                        }
                    });
        } else {
            sendMessage(sender, ChatColor.RED, "/rank list [groupname]");
        }
    }

    private void handleGroupCommand(CommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 4) {
            sendMessage(sender, ChatColor.RED, "Befehl nicht gefunden!");
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "add":
                handleGroupAdd(sender, args);
                break;
            case "del":
                handleGroupDelete(sender, args);
                break;
            case "setdefault":
                handleGroupSetDefault(sender, args);
                break;
            case "setprefix":
                handleGroupSetPrefix(sender, args);
                break;
            case "list":
                handleGroupList(sender, args);
                break;
            default:
                sendMessage(sender, ChatColor.RED, "Befehl nicht gefunden!");
                break;
        }
    }

    private void handleGroupAdd(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sendMessage(sender, ChatColor.RED, "/rank group add <groupname> <groupprefix>");
            return;
        }
        String groupName = args[2];
        String groupPrefix = args[3];
        if (BungeeRanks.addGroup(groupName, groupPrefix)) {
            sendMessage(sender, ChatColor.GREEN, "Die Gruppe \"" + groupName + "\" wurde erfolgreich erstellt!");
        } else {
            sendMessage(sender, ChatColor.RED, "Die Gruppe \"" + groupName + "\" konnte nicht erstellt werden!");
        }
    }

    private void handleGroupDelete(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendMessage(sender, ChatColor.RED, "/rank group del <groupname>");
            return;
        }
        String groupName = args[2];
        if (BungeeRanks.delGroup(groupName)) {
            sendMessage(sender, ChatColor.GREEN, "Die Gruppe \"" + groupName + "\" wurde erfolgreich gelöscht!");
        } else {
            sendMessage(sender, ChatColor.RED, "Die Gruppe \"" + groupName + "\" konnte nicht gelöscht werden!");
        }
    }

    private void handleGroupSetDefault(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sendMessage(sender, ChatColor.RED, "/rank group setdefault <groupname>");
            return;
        }
        String groupName = args[2];
        if (BungeeRanks.setDefaultRank(groupName)) {
            sendMessage(sender, ChatColor.GREEN, "Die Gruppe \"" + groupName + "\" ist nun der Default-Rang!");
        } else {
            sendMessage(sender, ChatColor.RED, "Die Gruppe \"" + groupName + "\" konnte nicht als Default-Rang festgelegt werden!");
        }
    }

    private void handleGroupSetPrefix(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sendMessage(sender, ChatColor.RED, "/rank group setprefix <groupname> <new prefix>");
            return;
        }
        String groupName = args[2];
        String newPrefix = args[3];
        if (BungeeRanks.setRankPrefix(groupName, newPrefix)) {
            sendMessage(sender, ChatColor.GREEN, "Die Gruppe \"" + groupName + "\" hat nun den Prefix \"" +
                    newPrefix.replace("&", "§") + "\"!");
        } else {
            sendMessage(sender, ChatColor.RED, "Der Prefix konnte nicht geändert werden.");
        }
    }

    private void handleGroupList(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sendMessage(sender, ChatColor.RED, "/rank group list");
            return;
        }
        sendMessage(sender, ChatColor.GOLD, "Ränge: " + ChatColor.GRAY);
        BungeeRanks.getAllRanks().forEach(rank -> sendMessage(sender, ChatColor.YELLOW, "- " + rank));
    }

    private void handlePermCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            sendMessage(sender, ChatColor.RED, "Befehl nicht gefunden!");
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "add":
                if (args.length != 4) {
                    sendMessage(sender, ChatColor.RED, "/rank perm add <groupname> <permission>");
                    return;
                }
                addPermission(sender, args[2], args[3]);
                break;
            case "del":
                if (args.length != 4) {
                    sendMessage(sender, ChatColor.RED, "/rank perm del <groupname> <permission>");
                    return;
                }
                deletePermission(sender, args[2], args[3]);
                break;
            case "clear":
                if (args.length != 3) {
                    sendMessage(sender, ChatColor.RED, "/rank perm clear <groupname>");
                    return;
                }
                clearPermissions(sender, args[2]);
                break;
            default:
                sendMessage(sender, ChatColor.RED, "Befehl nicht gefunden!");
                break;
        }
    }

    private void addPermission(CommandSender sender, String groupName, String permission) {
        String rankDisplayName = BungeeRanks.getRankDisplayName(groupName);
        if (rankDisplayName == null) {
            sendMessage(sender, ChatColor.RED, "Die Gruppe wurde nicht gefunden!");
            return;
        }

        if (BungeeRanks.addPerm(rankDisplayName, permission)) {
            sendMessage(sender, ChatColor.GREEN, "Die Berechtigung \"" + permission.toLowerCase() +
                    "\" wurde erfolgreich zur Gruppe \"" + rankDisplayName + "\" hinzugefügt.");
        } else {
            sendMessage(sender, ChatColor.RED, "Die Berechtigung \"" + permission.toLowerCase() + "\" wurde bereits hinzugefügt.");
        }
    }

    private void deletePermission(CommandSender sender, String groupName, String permission) {
        String rankDisplayName = BungeeRanks.getRankDisplayName(groupName);
        if (rankDisplayName == null) {
            sendMessage(sender, ChatColor.RED, "Die Gruppe wurde nicht gefunden!");
            return;
        }

        if (BungeeRanks.delPerm(rankDisplayName, permission)) {
            sendMessage(sender, ChatColor.GREEN, "Die Berechtigung \"" + permission.toLowerCase() + "\" wurde erfolgreich gelöscht.");
        } else {
            sendMessage(sender, ChatColor.RED, "Die Berechtigung \"" + permission.toLowerCase() + "\" wurde nicht gefunden.");
        }
    }

    private void clearPermissions(CommandSender sender, String groupName) {
        String rankDisplayName = BungeeRanks.getRankDisplayName(groupName);
        if (rankDisplayName == null) {
            sendMessage(sender, ChatColor.RED, "Die Gruppe wurde nicht gefunden!");
            return;
        }

        if (BungeeRanks.clearAllPerm(rankDisplayName)) {
            sendMessage(sender, ChatColor.GREEN, "Die Berechtigungen von \"" + rankDisplayName + "\" wurden erfolgreich gelöscht.");
        } else {
            sendMessage(sender, ChatColor.RED, "Die Berechtigungen von \"" + rankDisplayName + "\" konnten nicht gelöscht werden.");
        }
    }

    private void sendMessage(CommandSender receiver, ChatColor color, String message) {
        String formattedMessage = (receiver instanceof ProxiedPlayer) ? PREFIX_INGAME + color + message
                : PREFIX_SERVER + color + message;
        receiver.sendMessage(formattedMessage);
    }

    private void showHelp(CommandSender sender) {
        sendMessage(sender, ChatColor.AQUA, "-------" + ChatColor.DARK_AQUA + "Blockcraft - Ranks" + ChatColor.AQUA + "-------");
        sendMessage(sender, ChatColor.GREEN, "/rank help");
        sendMessage(sender, ChatColor.GREEN, "/rank set <player> <rank>");
        sendMessage(sender, ChatColor.GREEN, "/rank get [player]");
        sendMessage(sender, ChatColor.GREEN, "/rank list");
        sendMessage(sender, ChatColor.GREEN, "/rank group list");
        sendMessage(sender, ChatColor.GREEN, "/rank group add <groupname> <groupprefix>");
        sendMessage(sender, ChatColor.GREEN, "/rank group del <groupname>");
        sendMessage(sender, ChatColor.GREEN, "/rank group setprefix <groupname> <new prefix>");
        sendMessage(sender, ChatColor.GREEN, "/rank group setdefault <groupname>");
        sendMessage(sender, ChatColor.GREEN, "/rank perm <add|del> <groupname> <permission>");
        sendMessage(sender, ChatColor.GREEN, "/rank perm clear <groupname>");
        sendMessage(sender, ChatColor.GREEN, "");
    }
}
