package me.blockcraft.reports.commands;

import me.blockcraft.reports.Main;
import me.blockcraft.reports.util.ReportsManager;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

public class ReportCommand extends Command {
    public final HashMap<String, Long> cooldowns;
    final long cooldownTime;
    private final Main plugin;

    public ReportCommand(final String name, final Main plugin) {
        super(name);
        this.cooldownTime = 10L;
        this.cooldowns = new HashMap<>();
        this.plugin = plugin;
    }

    public static boolean removeLineFromFile(final String file, final String lineToRemove) {
        try {
            final File inFile = new File(file);
            if (!inFile.isFile()) {
                System.out.println("Parameter is not an existing file");
                return false;
            }
            final File tempFile = new File(inFile.getAbsolutePath() + ".tmp");
            final BufferedReader br = new BufferedReader(new FileReader(file));
            final PrintWriter pw = new PrintWriter(new FileWriter(tempFile));
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().equals(lineToRemove)) {
                    pw.println(line);
                    pw.flush();
                }
            }
            pw.close();
            br.close();
            if (!inFile.delete()) {
                System.out.println("Could not delete file");
                return false;
            }
            if (!tempFile.renameTo(inFile)) {
                System.out.println("Could not rename file");
            }
        } catch (final IOException ex2) {
            ex2.printStackTrace();
        }
        return true;
    }

    public ArrayList<String> playersInFile(final String file) {
        final ArrayList<String> players = new ArrayList<>();
        try {
            final BufferedReader in = new BufferedReader(new FileReader(this.plugin.getDataFolder() + File.separator + file));
            String str;
            while ((str = in.readLine()) != null) {
                players.add(str);
            }
            in.close();
        } catch (final IOException ex) {
            ex.printStackTrace();
        }
        return players;
    }

    @SuppressWarnings("deprecation")
    public void execute(final CommandSender sender, final String[] args) {
        if (sender instanceof ProxiedPlayer) {
            final ProxiedPlayer p = (ProxiedPlayer) sender;
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("info")) {
                    p.sendMessage("§7§m----------------§c Reports §7§m----------------");
                    p.sendMessage("§7Prefix: §e" + this.plugin.prefix + "Test Message");
                    p.sendMessage("§7Autor: §e" + this.plugin.getDescription().getAuthor());
                    p.sendMessage("§7Version: §e" + this.plugin.getDescription().getVersion());
                    p.sendMessage("§7Befehl: §e/report <Spieler> <Grund>");
                    p.sendMessage("§7§m----------------§c Reports §7§m----------------");
                } else if (args[0].equalsIgnoreCase("notify")) {
                    if (p.hasPermission("reports.notify")) {
                        if (this.playersInFile("IgnoredStaff.yml").contains(p.getName())) {
                            p.sendMessage(this.plugin.prefix + "Notifies: §cOFF");
                        } else {
                            p.sendMessage(this.plugin.prefix + "Notifies: §aON");
                        }
                    } else {
                        p.sendMessage(this.plugin.prefix + "Du hast nicht genügend Rechte!");
                    }
                } else {
                    p.sendMessage(this.plugin.prefix + "Nutze §c/report [Spieler] [Grund]");
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("notify")) {
                if (p.hasPermission("reports.notify")) {
                    if (args[0].equalsIgnoreCase("notify")) {
                        if (args[1].equalsIgnoreCase("off")) {
                            if (!this.playersInFile("IgnoredStaff.yml").contains(p.getName())) {
                                try {
                                    final BufferedWriter out = new BufferedWriter(new FileWriter(this.plugin.getDataFolder() + File.separator + "IgnoredStaff.yml", true));
                                    out.write(p.getName());
                                    out.newLine();
                                    out.close();
                                } catch (final IOException ex) {
                                    ex.printStackTrace();
                                }
                                p.sendMessage(this.plugin.prefix + "Du erhälst nun keine Report Notifies! (Notifies: §cOFF)");
                            } else {
                                p.sendMessage(this.plugin.prefix + "Du erhälst bereits keine Report Notifies! (Notifies: §cOFF)");
                            }
                        } else if (args[1].equalsIgnoreCase("on")) {
                            if (this.playersInFile("IgnoredStaff.yml").contains(p.getName())) {
                                final String file = this.plugin.getDataFolder() + File.separator + "IgnoredStaff.yml";
                                if (removeLineFromFile(file, p.getName())) {
                                    p.sendMessage(this.plugin.prefix + "Du erhälst nun wieder Report Notifies! (Notifies: §aON)");
                                } else {
                                    p.sendMessage(this.plugin.prefix + "§cEs gab einen fehler beim aktivieren der Notifies!");
                                }
                            } else {
                                p.sendMessage(this.plugin.prefix + "Du erhälst bereits Report Notifies! (Notifies: §aON)");
                            }
                        } else {
                            p.sendMessage(this.plugin.prefix + "Um die Notifies an oder aus zu schalten §c/reports notify [on|off]");
                        }
                    } else {
                        p.sendMessage(this.plugin.prefix + "Um die Notifies an oder aus zu schalten §c/reports notify [on|off]");
                    }
                } else {
                    p.sendMessage(this.plugin.prefix + "Du hast nicht genügend Rechte!");
                }
            } else if (args.length >= 2) {
                String name;
                if (this.plugin.getProxy().getPlayer(args[0]) == null) {
                    name = args[0];
                } else {
                    final ProxiedPlayer target = this.plugin.getProxy().getPlayer(args[0]);
                    name = target.getName();
                }
                if (name.equalsIgnoreCase(p.getName())) {
                    p.sendMessage(this.plugin.prefix + "Du kannst dich nicht selber Reporten!");
                    return;
                }
                if (ReportsManager.ReportExists(p.getName(), args[0])) {
                    p.sendMessage(this.plugin.prefix + "Du hast diesen Spieler bereits Reported!");
                    return;
                }
                if (!this.cooldowns.isEmpty() && this.cooldowns.containsKey(p.getName())) {
                    final long secondsLeft = this.cooldowns.get(p.getName()) / 1000L + this.cooldownTime - System.currentTimeMillis() / 1000L;
                    if (secondsLeft > 0L) {
                        p.sendMessage(this.plugin.prefix + "Du kannst diesen Befehl erst wieder in §c§l" + secondsLeft + " §7Sekunden nutzen!");
                        return;
                    }
                }
                StringBuilder message = new StringBuilder();
                for (int i = 1; i < args.length; ++i) {
                    message.append(args[i]).append(" ");
                }
                String server;
                if (this.plugin.getProxy().getPlayer(args[0]) != null) {
                    final ProxiedPlayer reported = this.plugin.getProxy().getPlayer(name);
                    server = reported.getServer().getInfo().getName();
                } else {
                    server = "§cNull";
                }
                final ArrayList<String> staff = this.playersInFile("IgnoredStaff.yml");
                final Integer anz = ReportsManager.anzReports(name);
                for (final ProxiedPlayer players : this.plugin.getProxy().getPlayers()) {
                    if (players.hasPermission("reports.notify") && !staff.contains(players.getName())) {
                        players.sendMessage(new TextComponent("§7§m--------------------- §cReports §7§m---------------------"));
                        players.sendMessage(new TextComponent("§e§l" + p.getName() + " §7hat §c§l" + args[0] + "§7 gemeldet!"));
                        players.sendMessage(new TextComponent("§7Grund: §e" + message));
                        players.sendMessage(new TextComponent("§7Server: §e" + server));
                        assert anz != null;
                        if (anz >= 1) {
                            players.sendMessage(new TextComponent("§7Anzahl der Reports: §e" + (anz + 1)));
                        }
                        players.sendMessage(new TextComponent("§7§m--------------------- §cReports §7§m---------------------"));
                    }
                }
                ReportsManager.addReport(p.getName(), args[0], message.toString(), server);
                this.cooldowns.put(p.getName(), System.currentTimeMillis());
                p.sendMessage(this.plugin.prefix + "§7Du hast den Spieler §c§l" + args[0] + "§7 erfolgreich gemeldet!");
            } else {
                p.sendMessage(this.plugin.prefix + "Nutze §c/report [Spieler] [Grund]");
            }
        } else {
            sender.sendMessage("§cDu musst ein Spieler sein!");
        }
    }
}
