package de.Blockcraft.BungeeRanks;

import net.craftminecraft.bungee.bungeeyaml.bukkitapi.InvalidConfigurationException;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class BungeeRanks extends Plugin {
    public static Database dbConfig;
    private static MySQL mySQL;

    public static String getRankName(String playerName) {
        return dbConfig.Ranks.stream()
                .filter(rank -> rank.startsWith(playerName + " "))
                .map(rank -> getRankDisplayName(rank.split(" ")[1]))
                .findFirst()
                .orElse(getDefaultRankName());
    }

    public static String getRankDisplayName(String rankName) {
        return dbConfig.Permissions.stream()
                .filter(rank -> rank.startsWith(rankName + " "))
                .map(rank -> rank.split(" ")[0])
                .findFirst()
                .orElse(null);
    }

    public static String getDefaultRankName() {
        return dbConfig.Permissions.stream()
                .filter(rank -> rank.contains(" "))
                .map(rank -> rank.split(" ")[0])
                .findFirst()
                .orElse(null);
    }

    public static void updatePlayerName(ProxiedPlayer player) {
        String displayName = player.getName();
        if (displayName.length() > 14) {
            displayName = displayName.substring(0, 14);
        }
        String color = getColorOf(player.getName());
        player.setDisplayName("§" + color + displayName);
    }

    public static void updateBukkitPermissions(ProxiedPlayer player) {
        if (dbConfig.UseForBukkit) {
            List<String> permissions = getRankPermissionsOf(player.getName());
            if (permissions != null) {
                permissions.forEach(permission -> player.chat("/RANK " + permission));
            }
        }
    }

    public static boolean setRank(String playerName, String rankName, String uuid) {
        String realRankName = getRankDisplayName(rankName);
        if (realRankName == null) return false;

        List<String> updatedRanks = dbConfig.Ranks.stream()
                .filter(rank -> !rank.startsWith(playerName + " "))
                .collect(Collectors.toList());

        if (!realRankName.equalsIgnoreCase(getDefaultRankName())) {
            updatedRanks.add(playerName + " " + realRankName);
        }
        dbConfig.Ranks = updatedRanks;

        try {
            dbConfig.save();
            if (mySQL != null && mySQL.isConnected() && uuid != null) {
                updateDatabasePermissions(rankName, uuid);
            }

            ProxyServer.getInstance().getPlayers().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(playerName))
                    .forEach(p -> {
                        updatePlayerName(p);
                        updateBukkitPermissions(p);
                    });
            return true;
        } catch (Exception e) {
            System.out.println("Failed to set rank: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static void updateDatabasePermissions(String rankName, String uuid) {
        String column = mapRankToColumn(rankName);
        if (column != null) {
            String setQuery = "UPDATE Permission SET " + column + " = 1 WHERE UUID = ?";
            mySQL.update(setQuery, uuid);
        }

        for (String rank : dbConfig.Permissions) {
            String currentRankName = rank.split(" ")[0];
            if (!currentRankName.equalsIgnoreCase(rankName)) {
                String otherColumn = mapRankToColumn(currentRankName);
                if (otherColumn != null) {
                    String resetQuery = "UPDATE Permission SET " + otherColumn + " = 0 WHERE UUID = ?";
                    mySQL.update(resetQuery, uuid);
                }
            }
        }
    }

    public static String playerExists(String name) {
        return ProxyServer.getInstance().getPlayers().stream()
                .map(ProxiedPlayer::getName)
                .filter(pName -> pName.equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> dbConfig.Ranks.stream()
                        .filter(rank -> rank.startsWith(name + " "))
                        .map(rank -> rank.split(" ")[0])
                        .findFirst()
                        .orElse(null));
    }

    public static String getRankPrefixOf(String playerName) {
        String rankName = getRankName(playerName);
        if (rankName == null) return null;

        return dbConfig.Permissions.stream()
                .filter(rank -> rank.startsWith(rankName + " "))
                .map(rank -> rank.split(" ")[1])
                .findFirst()
                .orElse(null);
    }

    public static String getRankPrefix(String rank) {
        String rankDisplayName = getRankDisplayName(rank);
        if (rankDisplayName == null) return null;

        return dbConfig.Permissions.stream()
                .filter(r -> r.startsWith(rankDisplayName + " "))
                .map(r -> r.split(" ")[1])
                .findFirst()
                .orElse(null);
    }

    public static List<String> getAllRanks() {
        return dbConfig.Permissions.stream()
                .map(rank -> rank.split(" ")[0])
                .collect(Collectors.toList());
    }

    public static boolean hasPermissions(String player, String permission) {
        List<String> playerPerms = getRankPermissionsOf(player);
        if (playerPerms == null) return false;

        Set<String> deniedPerms = new HashSet<>();
        boolean hasPerm = false;

        for (String perm : playerPerms) {
            if (perm.startsWith("-")) {
                deniedPerms.add(perm.substring(1).toLowerCase());
                continue;
            }
            if (perm.equals("*") || perm.equalsIgnoreCase(permission) || wildcardMatch(perm, permission)) {
                hasPerm = true;
            }
        }

        if (deniedPerms.contains("*") || deniedPerms.contains(permission.toLowerCase()) || wildcardMatchSet(deniedPerms, permission)) {
            hasPerm = false;
        }

        return hasPerm;
    }

    private static boolean wildcardMatch(String pattern, String permission) {
        String[] patternParts = pattern.split("\\.");
        String[] permParts = permission.split("\\.");

        if (patternParts.length != permParts.length) return false;

        for (int i = 0; i < patternParts.length; i++) {
            if (!patternParts[i].equals("*") && !patternParts[i].equalsIgnoreCase(permParts[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean wildcardMatchSet(Set<String> deniedPerms, String permission) {
        for (String denied : deniedPerms) {
            if (wildcardMatch(denied, permission)) {
                return true;
            }
        }
        return false;
    }

    public static List<String> getRankPermissionsOf(String playerName) {
        String rankName = getRankName(playerName);
        if (rankName == null) return null;

        return dbConfig.Permissions.stream()
                .filter(rank -> rank.startsWith(rankName + " "))
                .flatMap(rank -> Arrays.stream(rank.split(" ")[2].split(",")))
                .filter(perm -> !perm.isEmpty())
                .collect(Collectors.toList());
    }

    public static List<String> getRankPermissions(String rank) {
        String rankDisplayName = getRankDisplayName(rank);
        if (rankDisplayName == null) return null;

        return dbConfig.Permissions.stream()
                .filter(r -> r.startsWith(rankDisplayName + " "))
                .flatMap(r -> Arrays.stream(r.split(" ")[2].split(",")))
                .filter(perm -> !perm.isEmpty())
                .collect(Collectors.toList());
    }

    public static String getColorOf(String playerName) {
        String rankPrefix = getRankPrefixOf(playerName);
        if (rankPrefix == null || !rankPrefix.startsWith("&")) {
            return "f";
        }
        return rankPrefix.substring(1, 2);
    }

    public static List<String> getPlayerRanks() {
        return dbConfig.Ranks.stream()
                .filter(rank -> rank.contains(" "))
                .map(rank -> rank.split(" ")[0] + ": " + rank.split(" ")[1])
                .collect(Collectors.toList());
    }

    public static boolean addGroup(String groupName, String groupPrefix) {
        if (getRankDisplayName(groupName) != null) {
            return false;
        }
        dbConfig.Permissions.add(groupName + " " + groupPrefix + " -");
        try {
            dbConfig.save();
            return true;
        } catch (InvalidConfigurationException e) {
            System.out.println("Failed to add group: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean delGroup(String groupName) {
        String rankName = getRankDisplayName(groupName);
        if (rankName == null) {
            return false;
        }

        dbConfig.Permissions.removeIf(rank -> rank.startsWith(rankName + " "));
        dbConfig.Ranks.removeIf(rank -> rank.split(" ")[1].equalsIgnoreCase(rankName));

        // Reset players to default rank
        dbConfig.Ranks.stream()
                .filter(rank -> rank.split(" ")[1].equalsIgnoreCase(rankName))
                .map(rank -> rank.split(" ")[0])
                .forEach(player -> setRank(player, getDefaultRankName(), ""));

        try {
            dbConfig.save();
            return true;
        } catch (InvalidConfigurationException e) {
            System.out.println("Failed to delete group: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean addPerm(String groupName, String permission) {
        String rankName = getRankDisplayName(groupName);
        if (rankName == null) return false;

        List<String> permissions = getRankPermissions(rankName);
        if (permissions == null || permissions.contains(permission.toLowerCase())) {
            return false;
        }

        dbConfig.Permissions = dbConfig.Permissions.stream()
                .map(rank -> {
                    if (rank.startsWith(rankName + " ")) {
                        String[] parts = rank.split(" ", 3);
                        String updatedPerms = parts[2].replace("-", "") + "," + permission.toLowerCase();
                        return parts[0] + " " + parts[1] + " " + updatedPerms;
                    }
                    return rank;
                })
                .collect(Collectors.toList());

        try {
            dbConfig.save();
            return true;
        } catch (InvalidConfigurationException e) {
            System.out.println("Failed to add permission: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean delPerm(String groupName, String permission) {
        String rankName = getRankDisplayName(groupName);
        if (rankName == null) return false;

        boolean removed = false;
        List<String> updatedPermissions = new ArrayList<>();

        for (String rank : dbConfig.Permissions) {
            if (rank.startsWith(rankName + " ")) {
                String[] parts = rank.split(" ", 3);
                List<String> perms = new ArrayList<>(Arrays.asList(parts[2].toLowerCase().split(",")));
                if (perms.remove(permission.toLowerCase())) {
                    removed = true;
                }
                if (perms.isEmpty()) {
                    perms.add("-");
                }
                String updatedPerms = String.join(",", perms);
                updatedPermissions.add(parts[0] + " " + parts[1] + " " + updatedPerms);
            } else {
                updatedPermissions.add(rank);
            }
        }

        if (!removed) {
            return false;
        }

        dbConfig.Permissions = updatedPermissions;

        try {
            dbConfig.save();
            return true;
        } catch (InvalidConfigurationException e) {
            System.out.println("Failed to delete permission: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean clearAllPerm(String groupName) {
        String rankName = getRankDisplayName(groupName);
        if (rankName == null) return false;

        boolean found = false;
        List<String> updatedPermissions = new ArrayList<>();

        for (String rank : dbConfig.Permissions) {
            if (rank.startsWith(rankName + " ")) {
                updatedPermissions.add(rankName + " " + getRankPrefix(rankName) + " -");
                found = true;
            } else {
                updatedPermissions.add(rank);
            }
        }

        if (!found) {
            return false;
        }

        dbConfig.Permissions = updatedPermissions;

        try {
            dbConfig.save();
            return true;
        } catch (InvalidConfigurationException e) {
            System.out.println("Failed to clear permissions: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean setRankPrefix(String groupName, String prefix) {
        String rankName = getRankDisplayName(groupName);
        if (rankName == null) return false;

        List<String> updatedPermissions = dbConfig.Permissions.stream()
                .map(rank -> {
                    if (rank.startsWith(rankName + " ")) {
                        String[] parts = rank.split(" ", 3);
                        return parts[0] + " " + prefix + " " + parts[2];
                    }
                    return rank;
                })
                .collect(Collectors.toList());

        boolean found = dbConfig.Permissions.stream().anyMatch(rank -> rank.startsWith(rankName + " "));

        if (!found) {
            return false;
        }

        dbConfig.Permissions = updatedPermissions;

        try {
            dbConfig.save();
            return true;
        } catch (InvalidConfigurationException e) {
            System.out.println("Failed to set rank prefix: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean setDefaultRank(String rank) {
        String rankName = getRankDisplayName(rank);
        if (rankName == null) return false;

        List<String> updatedPermissions = new ArrayList<>();
        Optional<String> targetRank = dbConfig.Permissions.stream()
                .filter(r -> r.startsWith(rankName + " "))
                .findFirst();

        if (targetRank.isEmpty()) {
            return false;
        }

        updatedPermissions.add(targetRank.get());
        dbConfig.Permissions.stream()
                .filter(r -> !r.startsWith(rankName + " "))
                .forEach(updatedPermissions::add);

        dbConfig.Permissions = updatedPermissions;

        try {
            dbConfig.save();
            return true;
        } catch (InvalidConfigurationException e) {
            System.out.println("Failed to set default rank: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static void createPlayer(final String uuid, String name) {
        String query = "INSERT INTO Permission(UUID, NAME, PREMIUM, YOUTUBE, DEV, ADMIN, OWNER) VALUES (?, " + name + ", '0', '0', '0', '0', '0')";
        mySQL.update(query, uuid);
    }

    public static String getUUID(String playerName) {
        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerName);
        if (player != null) {
            System.out.println("Player is online: " + playerName + " UUID: " + player.getUniqueId());
            return player.getUniqueId().toString();
        }

        if (mySQL != null && mySQL.isConnected()) {
            String query = "SELECT UUID FROM Permission WHERE NAME = ?";
            try (PreparedStatement stmt = MySQL.con.prepareStatement(query)) {
                stmt.setString(1, playerName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("UUID");
                    }
                }
            } catch (SQLException e) {
                System.out.println("Failed to get UUID for player " + playerName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        return null;
    }

    public static String mapRankToColumn(String rankName) {
        return switch (rankName.toUpperCase()) {
            case "PREMIUM" -> "PREMIUM";
            case "YOUTUBE" -> "YOUTUBE";
            case "DEV" -> "DEV";
            case "ADMIN" -> "ADMIN";
            case "OWNER" -> "OWNER";
            default -> null;
        };
    }

    @Override
    public void onEnable() {
        dbConfig = new Database(this);
        initializeDefaultPermissions();

        mySQL = new MySQL(this);
        mySQL.connect();
        mySQL.createTable();

        ProxyServer.getInstance().getPluginManager().registerListener(this, new PlayerListener(this));
        ProxyServer.getInstance().getPluginManager().registerListener(this, new PluginListener());
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new Command_Rank("rank"));

        System.out.println("##################################");
        System.out.println("#  BungeeRanks wurde aktiviert!  #");
        System.out.println("#      Plugin by Blockcraft.     #");
        System.out.println("##################################");
    }

    @Override
    public void onDisable() {
        if (mySQL != null) {
            mySQL.close();
        }
        System.out.println("##################################");
        System.out.println("# BungeeRanks wurde deaktiviert! #");
        System.out.println("#      Plugin by Blockcraft.     #");
        System.out.println("##################################");
    }

    private void initializeDefaultPermissions() {
        if (dbConfig.Permissions.isEmpty()) {
            dbConfig.Permissions.addAll(Arrays.asList(
                    "Player &2Player bungeecord.command.list,bungeecord.command.server",
                    "Premium &6Premium bungeecord.command.server,bungeecord.command.list,bungeecord.essentials.chatcolor",
                    "YouTuber &5&7You&cTuber bungeecord.command.alert,bungeecord.command.list,bungeecord.command.server,bungeecord.command.send,bungeecord.essentials.chatcolor",
                    "Dev &bDeveloper bungeecord.essentials.*, -bungeecord.essentials.lock, -bungeecord.essentials.op, bungeecord.command.*, -bungeecord.command.reload, -bungeecord.command.end, bungeecord.rank.*",
                    "Admin &9Admin bungeecord.essentials.*, -bungeecord.essentials.lock, -bungeecord.essentials.op, bungeecord.command.*, bungeecord.login.*, -bungeecord.command.reload, -bungeecord.command.end",
                    "CoOwner &cCoOwner *,-bungeecord.command.reload,-bungeecord.command.end",
                    "Owner &4Administrator *"
            ));
            try {
                dbConfig.save();
            } catch (InvalidConfigurationException e) {
                System.out.println("Failed to save default permissions: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
