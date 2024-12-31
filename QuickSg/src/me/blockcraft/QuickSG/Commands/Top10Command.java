package me.blockcraft.QuickSG.Commands;

import me.blockcraft.MySQL.MySQL;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.sql.ResultSet;
import java.sql.SQLException;

public class Top10Command
        implements Listener,
        CommandExecutor {
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player p = (Player) sender;
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("wins")) {
                p.sendMessage("\u00a77\u00a7m---------------------------------");
                try {
                    ResultSet rs = MySQL.getResult("SELECT `WIN`,`NAME` FROM `qsg_stats` ORDER BY WIN DESC LIMIT 10;");
                    int zahl = 0;
                    while (rs.next()) {
                        if (++zahl == 1) {
                            p.sendMessage("\u00a7e\u00a7l#1 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + "\u00a77 ( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 2) {
                            p.sendMessage("\u00a7r#2 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 3) {
                            p.sendMessage("\u00a7r#3 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 4) {
                            p.sendMessage("\u00a7r#4 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 5) {
                            p.sendMessage("\u00a7r#5 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 6) {
                            p.sendMessage("\u00a7r#6 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 7) {
                            p.sendMessage("\u00a7r#7 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 8) {
                            p.sendMessage("\u00a7r#8 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 9) {
                            p.sendMessage("\u00a7r#9 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl != 10) continue;
                        p.sendMessage("\u00a7r#10 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                    }
                } catch (SQLException x) {
                    x.printStackTrace();
                    System.out.println("FEHLER [4]");
                }
                p.sendMessage("\u00a77\u00a7m---------------------------------");
            } else if (args[0].equalsIgnoreCase("deaths")) {
                p.sendMessage("\u00a77\u00a7m---------------------------------");
                try {
                    ResultSet rs = MySQL.getResult("SELECT `DEATHS`,`NAME` FROM `qsg_stats` ORDER BY DEATHS DESC LIMIT 10;");
                    int zahl = 0;
                    while (rs.next()) {
                        if (++zahl == 1) {
                            p.sendMessage("\u00a7e\u00a7l#1 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + "\u00a77 ( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 2) {
                            p.sendMessage("\u00a7r#2 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 3) {
                            p.sendMessage("\u00a7r#3 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 4) {
                            p.sendMessage("\u00a7r#4 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 5) {
                            p.sendMessage("\u00a7r#5 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 6) {
                            p.sendMessage("\u00a7r#6 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 7) {
                            p.sendMessage("\u00a7r#7 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 8) {
                            p.sendMessage("\u00a7r#8 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 9) {
                            p.sendMessage("\u00a7r#9 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl != 10) continue;
                        p.sendMessage("\u00a7r#10 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                    }
                } catch (SQLException x) {
                    x.printStackTrace();
                    System.out.println("FEHLER [4]");
                }
                p.sendMessage("\u00a77\u00a7m---------------------------------");
            } else if (args[0].equalsIgnoreCase("kills")) {
                p.sendMessage("\u00a77\u00a7m---------------------------------");
                try {
                    ResultSet rs = MySQL.getResult("SELECT `KILLS`,`NAME` FROM `qsg_stats` ORDER BY KILLS DESC LIMIT 10;");
                    int zahl = 0;
                    while (rs.next()) {
                        if (++zahl == 1) {
                            p.sendMessage("\u00a7e\u00a7l#1 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + "\u00a77 ( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 2) {
                            p.sendMessage("\u00a7r#2 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 3) {
                            p.sendMessage("\u00a7r#3 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 4) {
                            p.sendMessage("\u00a7r#4 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 5) {
                            p.sendMessage("\u00a7r#5 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 6) {
                            p.sendMessage("\u00a7r#6 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 7) {
                            p.sendMessage("\u00a7r#7 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 8) {
                            p.sendMessage("\u00a7r#8 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 9) {
                            p.sendMessage("\u00a7r#9 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl != 10) continue;
                        p.sendMessage("\u00a7r#10 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                    }
                } catch (SQLException x) {
                    x.printStackTrace();
                    System.out.println("FEHLER [4]");
                }
                p.sendMessage("\u00a77\u00a7m---------------------------------");
            } else if (args[0].equalsIgnoreCase("Loses")) {
                p.sendMessage("\u00a77\u00a7m---------------------------------");
                try {
                    ResultSet rs = MySQL.getResult("SELECT `LOSE`,`NAME` FROM `qsg_stats` ORDER BY LOSE DESC LIMIT 10;");
                    int zahl = 0;
                    while (rs.next()) {
                        if (++zahl == 1) {
                            p.sendMessage("\u00a7e\u00a7l#1 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + "\u00a77 ( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 2) {
                            p.sendMessage("\u00a7r#2 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 3) {
                            p.sendMessage("\u00a7r#3 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 4) {
                            p.sendMessage("\u00a7r#4 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 5) {
                            p.sendMessage("\u00a7r#5 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 6) {
                            p.sendMessage("\u00a7r#6 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 7) {
                            p.sendMessage("\u00a7r#7 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 8) {
                            p.sendMessage("\u00a7r#8 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 9) {
                            p.sendMessage("\u00a7r#9 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl != 10) continue;
                        p.sendMessage("\u00a7r#10 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                    }
                } catch (SQLException x) {
                    x.printStackTrace();
                    System.out.println("FEHLER [4]");
                }
                p.sendMessage("\u00a77\u00a7m---------------------------------");
            } else if (args[0].equalsIgnoreCase("points")) {
                p.sendMessage("\u00a77\u00a7m---------------------------------");
                try {
                    ResultSet rs = MySQL.getResult("SELECT `COINS`,`NAME` FROM `qsg_stats` ORDER BY COINS DESC LIMIT 10;");
                    int zahl = 0;
                    while (rs.next()) {
                        if (++zahl == 1) {
                            p.sendMessage("\u00a7e\u00a7l#1 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + "\u00a77 ( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 2) {
                            p.sendMessage("\u00a7r#2 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 3) {
                            p.sendMessage("\u00a7r#3 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 4) {
                            p.sendMessage("\u00a7r#4 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 5) {
                            p.sendMessage("\u00a7r#5 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 6) {
                            p.sendMessage("\u00a7r#6 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 7) {
                            p.sendMessage("\u00a7r#7 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 8) {
                            p.sendMessage("\u00a7r#8 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl == 9) {
                            p.sendMessage("\u00a7r#9 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                        }
                        if (zahl != 10) continue;
                        p.sendMessage("\u00a7r#10 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                    }
                } catch (SQLException x) {
                    x.printStackTrace();
                    System.out.println("FEHLER [4]");
                }
                p.sendMessage("\u00a77\u00a7m---------------------------------");
            }
        }
        return false;
    }
}

