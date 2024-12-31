package me.blockcraft.perms.MySQL;

import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static me.blockcraft.perms.Main.luckPerms;
import static org.bukkit.Bukkit.getLogger;

public class API {
    public static boolean playerExists(final String uuid) {
        try {
            final ResultSet rs = MySQL.getResult("SELECT * FROM Permission WHERE UUID='" + uuid + "'");
            return rs.next() && rs.getString("UUID") != null;
        } catch (SQLException e) {
            MySQL.close();
            MySQL.setStandardMySQL();
            MySQL.readMySQL();
            MySQL.connect();
            MySQL.createTable();
            return false;
        }
    }

    public static void createPlayer(final String uuid) {
        if (!playerExists(uuid)) {
            String name = Bukkit.getPlayer(UUID.fromString(uuid)).getName();

            MySQL.update("INSERT INTO Permission(UUID, NAME, YOUTUBE, ADMIN, PREMIUM, DEV, OWNER) " +
                    "VALUES ('" + uuid + "', '" + name + "', 0, 0, 0, 0, 0);");
            luckPerms.getUserManager().modifyUser(UUID.fromString(uuid), user -> {
                Group userGroup = luckPerms.getGroupManager().getGroup("player");
                if (userGroup == null) {
                    System.out.println("Group 'player' not found!");
                    return;
                }
                Node userNode = InheritanceNode.builder(userGroup).build();
                user.data().add(userNode);
            });
        }
    }

    private static Integer getPermission(final String uuid, final String column) {
        int value = 0;
        if (playerExists(uuid)) {
            try {
                final ResultSet rs = MySQL.getResult("SELECT " + column + " FROM Permission WHERE UUID='" + uuid + "'");
                if (rs.next()) {
                    value = rs.getInt(column);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            // Assuming default rank as 'player' with no permissions
            createPlayer(uuid);
            value = getPermission(uuid, column);
        }
        return value;
    }

    private static void resetRole(final String uuid) {
        if (playerExists(uuid)) {
            MySQL.update("UPDATE Permission SET YOUTUBE=0, ADMIN=0, PREMIUM=0, DEV=0, OWNER=0 WHERE UUID='" + uuid + "'");
        } else {
            createPlayer(uuid);
            resetRole(uuid);
        }
    }

    private static void setRole(final String uuid, final String name, final String role) {
        if (playerExists(uuid)) {
            resetRole(uuid);

            // Set the desired role
            String setQuery = "UPDATE Permission SET " + role + "=1, NAME='" + name + "' WHERE UUID='" + uuid + "'";
            MySQL.update(setQuery);

            if (!Objects.equals(role, "player")) {
                assignLuckPermsGroup(UUID.fromString(uuid), role.toLowerCase());
            }
        } else {
            createPlayer(uuid);
            setRole(uuid, name, role);
        }
    }

    public static void setAdmin(final String uuid, final String name) {
        setRole(uuid, name, "ADMIN");
    }

    public static void setYT(final String uuid, final String name) {
        setRole(uuid, name, "YOUTUBE");
    }

    public static void setPremium(final String uuid, final String name) {
        setRole(uuid, name, "PREMIUM");
    }

    public static void setDev(final String uuid, final String name) {
        setRole(uuid, name, "DEV");
    }

    public static void setOwner(final String uuid, final String name) {
        setRole(uuid, name, "OWNER");
    }

    public static void setPlayer(final String uuid, final String name) {
        resetRole(uuid);
    }

    public static boolean isAdmin(final Player p) {
        return getPermission(p.getUniqueId().toString(), "ADMIN") == 1;
    }

    public static boolean isYT(final Player p) {
        return getPermission(p.getUniqueId().toString(), "YOUTUBE") == 1;
    }

    public static boolean isPremium(final Player p) {
        return getPermission(p.getUniqueId().toString(), "PREMIUM") == 1;
    }

    public static boolean isDev(final Player p) {
        return getPermission(p.getUniqueId().toString(), "DEV") == 1;
    }

    public static boolean isOwner(final Player p) {
        return getPermission(p.getUniqueId().toString(), "OWNER") == 1;
    }

    public static void assignLuckPermsGroup(UUID uuid, String rank) {
        CompletableFuture.runAsync(() -> luckPerms.getUserManager().modifyUser(uuid, (User user) -> {
            if (user == null) {
                // User is not loaded, attempt to load them
                user = luckPerms.getUserManager().loadUser(uuid).join();
                if (user == null) {
                    getLogger().severe("Failed to load user with UUID: " + uuid);
                    return;
                }
            }

            String groupName = rank.toLowerCase();
            Group group = luckPerms.getGroupManager().getGroup(groupName.toLowerCase());
            if (group == null) {
                getLogger().severe("Group '" + groupName.toLowerCase() + "' not found in LuckPerms!");
                return;
            }

            if (user.getPrimaryGroup().equalsIgnoreCase(groupName)) {
                return;
            }

            // Remove user from all other groups except for the primary group if necessary
            user.data().clear();

            System.out.println("Auto-assigning group " + groupName + " to " + user.getUniqueId());
            user.data().add(InheritanceNode.builder(group).build());
        }));
    }
}
