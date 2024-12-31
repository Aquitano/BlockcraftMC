package me.blockcraft.lobby.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import static me.blockcraft.perms.MySQL.API.*;

public class Board {
    public static Scoreboard sb;

    public static void sendBoard(final Player p) {
        String teamName;

        if (isOwner(p)) {
            teamName = "00007Owner";
        } else if (isAdmin(p)) {
            teamName = "00000Admin";
        } else if (isDev(p)) {
            teamName = "00001Dev";
        } else if (isYT(p)) {
            teamName = "00005SYoutuber";
        } else if (isPremium(p)) {
            teamName = "00006Premium";
        } else {
            teamName = "Spieler";
        }

        Team team = sb.getTeam(teamName);
        if (team != null) {
            // Remove the player from all other teams to prevent multiple team memberships
            for (Team existingTeam : sb.getTeams()) {
                existingTeam.removeEntry(p.getName());
            }

            // Add the player to the determined team
            team.addEntry(p.getName());
            p.setScoreboard(sb);
        } else {
            // Handle the case where the team does not exist
            p.sendMessage("\u00a78[\u00a74Permissions\u00a78] \u00a7cFehler: Team '" + teamName + "' existiert nicht.");
        }
    }

    public static void setupTab() {
        (Board.sb = Bukkit.getScoreboardManager().getNewScoreboard()).registerNewTeam("00000Admin");

        registerTeam("00000Admin", "\u00a74");          // Red for Admin
        registerTeam("00001Dev", "\u00a7b");            // Aqua for Dev
        registerTeam("00005SYoutuber", "\u00a75");      // Purple for YouTuber
        registerTeam("00006Premium", "\u00a76");        // Gold for Premium
        registerTeam("00007Owner", "\u00a7e");          // Yellow for Owner
        registerTeam("Spieler", "\u00a77");             // Gray for Players

        // Set the scoreboard for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendBoard(player);
        }
    }

    private static void registerTeam(String teamName, String prefix) {
        if (sb.getTeam(teamName) == null) {
            Team team = sb.registerNewTeam(teamName);
            team.setPrefix(prefix);
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
        }
    }
}
