package me.blockcraft.reportsmenu.commands;

import me.blockcraft.reportsmenu.Main;
import me.blockcraft.reportsmenu.util.ReportsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;

public class ReportsCommand implements CommandExecutor {
    private final Main plugin;

    public ReportsCommand(final Main plugin) {
        this.plugin = plugin;
    }

    public static void openReportsInv(final Player player, final Integer repi) {
        final Inventory inv = Bukkit.createInventory(null, 54, "§cReports");
        final Inventory inv2 = Bukkit.createInventory(null, 54, "§cReports");
        final Inventory inv3 = Bukkit.createInventory(null, 54, "§cReports");
        final Inventory inv4 = Bukkit.createInventory(null, 54, "§cReports");
        final Inventory inv5 = Bukkit.createInventory(null, 54, "§cReports");
        final ItemStack exit = new ItemStack(Material.BARRIER, 1);
        final ItemMeta exitmeta = exit.getItemMeta();
        exitmeta.setDisplayName("§4Schließen");
        exit.setItemMeta(exitmeta);
        final ItemStack back = new ItemStack(Material.ARROW, 1);
        final ItemMeta backmeta = back.getItemMeta();
        backmeta.setDisplayName("§cZurück");
        final ArrayList<String> backlore = new ArrayList<>();
        backlore.add("§7Geht eine Seite zurück!");
        backmeta.setLore(backlore);
        back.setItemMeta(backmeta);
        final ItemStack stick = new ItemStack(Material.STICK, 1);
        final ItemMeta stickmeta = stick.getItemMeta();
        stickmeta.setDisplayName("§7Knockback-Stick");
        stickmeta.addEnchant(Enchantment.KNOCKBACK, 1, true);
        stick.setItemMeta(stickmeta);
        final ItemStack pf = new ItemStack(Material.PAPER, 1);
        final ItemMeta pfmeta = pf.getItemMeta();
        pfmeta.setDisplayName("§aNächste Seite");
        final ArrayList<String> pflore = new ArrayList<>();
        pflore.add("§7Geht eine Seite nach vorne!");
        pfmeta.setLore(pflore);
        pf.setItemMeta(pfmeta);
        final ItemStack pb = new ItemStack(Material.PAPER, 1);
        final ItemMeta pbmeta = pb.getItemMeta();
        pbmeta.setDisplayName("§cVorherige Seite");
        final ArrayList<String> pblore = new ArrayList<>();
        pblore.add("§7Geht eine Seite zurück!");
        pbmeta.setLore(pblore);
        pb.setItemMeta(pbmeta);
        final ItemStack p = new ItemStack(Material.BOOK, 1);
        final ItemMeta pmeta = p.getItemMeta();
        pmeta.setDisplayName("§6Aktuelle Seite");
        final ArrayList<String> plore = new ArrayList<>();
        plore.add("§7Aktuelle Seite: §e1");
        pmeta.setLore(plore);
        p.setItemMeta(pmeta);
        final ItemStack p2 = new ItemStack(Material.BOOK, 2);
        final ItemMeta p2meta = p2.getItemMeta();
        p2meta.setDisplayName("§6Aktuelle Seite");
        final ArrayList<String> p2lore = new ArrayList<>();
        p2lore.add("§7Aktuelle Seite: §e2");
        p2meta.setLore(p2lore);
        p2.setItemMeta(p2meta);
        final ItemStack p3 = new ItemStack(Material.BOOK, 3);
        final ItemMeta p3meta = p3.getItemMeta();
        p3meta.setDisplayName("§6Aktuelle Seite");
        final ArrayList<String> p3lore = new ArrayList<>();
        p3lore.add("§7Aktuelle Seite: §e3");
        p3meta.setLore(p3lore);
        p3.setItemMeta(p3meta);
        final ItemStack p4 = new ItemStack(Material.BOOK, 4);
        final ItemMeta p4meta = p4.getItemMeta();
        p4meta.setDisplayName("§6Aktuelle Seite");
        final ArrayList<String> p4lore = new ArrayList<>();
        p4lore.add("§7Aktuelle Seite: §e4");
        p4meta.setLore(p4lore);
        p4.setItemMeta(p4meta);
        final ItemStack p5 = new ItemStack(Material.BOOK, 5);
        final ItemMeta p5meta = p5.getItemMeta();
        p5meta.setDisplayName("§6Aktuelle Seite");
        final ArrayList<String> p5lore = new ArrayList<>();
        p5lore.add("§7Aktuelle Seite: §e5");
        p5meta.setLore(p5lore);
        p5.setItemMeta(p5meta);
        final ItemStack line = new ItemStack(Material.IRON_FENCE, 1);
        final ItemMeta linemeta = line.getItemMeta();
        linemeta.setDisplayName(" ");
        line.setItemMeta(linemeta);
        final ArrayList<Integer> id = ReportsManager.getIDs();
        final ArrayList<String> reported = ReportsManager.getReportLists("Reportedname");
        final ArrayList<String> reporter = ReportsManager.getReportLists("Reportername");
        final ArrayList<String> grund = ReportsManager.getReportLists("Grund");
        final ArrayList<String> server = ReportsManager.getReportLists("Server");
        final ArrayList<String> status = ReportsManager.getReportLists("Status");
        if (id.isEmpty()) {
            player.sendMessage(Main.getInstance().prefix + "Es gibt aktuell keine Reports!");
            return;
        }
        int i;
        for (i = 0; i <= 35; ++i) {
            if (id.isEmpty()) {
                break;
            }
            final ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1);
            playerhead.setDurability((short) 3);
            final SkullMeta playerheadmeta = (SkullMeta) playerhead.getItemMeta();
            playerheadmeta.setOwner(reported.get(0));
            playerheadmeta.setDisplayName("§a" + reported.get(0));
            final ArrayList<String> playerheadlore = new ArrayList<>();
            playerheadlore.add("§7Reporter: §e" + reporter.get(0));
            playerheadlore.add("§7Grund: §e" + grund.get(0));
            playerheadlore.add("§7Server: §e" + server.get(0));
            playerheadlore.add("§7ReportID: §e" + id.get(0));
            playerheadlore.add("§7Status: §e" + ChatColor.translateAlternateColorCodes('&', status.get(0)));
            playerheadmeta.setLore(playerheadlore);
            playerhead.setItemMeta(playerheadmeta);
            inv.setItem(i, playerhead);
            id.remove(0);
            reported.remove(0);
            reporter.remove(0);
            grund.remove(0);
            server.remove(0);
            status.remove(0);
        }
        while (i <= 71) {
            if (id.isEmpty()) {
                break;
            }
            final ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1);
            playerhead.setDurability((short) 3);
            final SkullMeta playerheadmeta = (SkullMeta) playerhead.getItemMeta();
            playerheadmeta.setOwner(reported.get(0));
            playerheadmeta.setDisplayName("§a" + reported.get(0));
            final ArrayList<String> playerheadlore = new ArrayList<>();
            playerheadlore.add("§7Reporter: §e" + reporter.get(0));
            playerheadlore.add("§7Grund: §e" + grund.get(0));
            playerheadlore.add("§7Server: §e" + server.get(0));
            playerheadlore.add("§7ReportID: §e" + id.get(0));
            playerheadlore.add("§7Status: §e" + ChatColor.translateAlternateColorCodes('&', status.get(0)));
            playerheadmeta.setLore(playerheadlore);
            playerhead.setItemMeta(playerheadmeta);
            inv2.setItem(i - 36, playerhead);
            id.remove(0);
            reported.remove(0);
            reporter.remove(0);
            grund.remove(0);
            server.remove(0);
            ++i;
        }
        while (i <= 107) {
            if (id.isEmpty()) {
                break;
            }
            final ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1);
            playerhead.setDurability((short) 3);
            final SkullMeta playerheadmeta = (SkullMeta) playerhead.getItemMeta();
            playerheadmeta.setOwner(reported.get(0));
            playerheadmeta.setDisplayName("§a" + reported.get(0));
            final ArrayList<String> playerheadlore = new ArrayList<>();
            playerheadlore.add("§7Reporter: §e" + reporter.get(0));
            playerheadlore.add("§7Grund: §e" + grund.get(0));
            playerheadlore.add("§7Server: §e" + server.get(0));
            playerheadlore.add("§7ReportID: §e" + id.get(0));
            playerheadlore.add("§7Status: §e" + ChatColor.translateAlternateColorCodes('&', status.get(0)));
            playerheadmeta.setLore(playerheadlore);
            playerhead.setItemMeta(playerheadmeta);
            inv3.setItem(i - 72, playerhead);
            id.remove(0);
            reported.remove(0);
            reporter.remove(0);
            grund.remove(0);
            server.remove(0);
            status.remove(0);
            ++i;
        }
        while (i <= 143) {
            if (id.isEmpty()) {
                break;
            }
            final ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1);
            playerhead.setDurability((short) 3);
            final SkullMeta playerheadmeta = (SkullMeta) playerhead.getItemMeta();
            playerheadmeta.setOwner(reported.get(0));
            playerheadmeta.setDisplayName("§a" + reported.get(0));
            final ArrayList<String> playerheadlore = new ArrayList<>();
            playerheadlore.add("§7Reporter: §e" + reporter.get(0));
            playerheadlore.add("§7Grund: §e" + grund.get(0));
            playerheadlore.add("§7Server: §e" + server.get(0));
            playerheadlore.add("§7ReportID: §e" + id.get(0));
            playerheadlore.add("§7Status: §e" + ChatColor.translateAlternateColorCodes('&', status.get(0)));
            playerheadmeta.setLore(playerheadlore);
            playerhead.setItemMeta(playerheadmeta);
            inv4.setItem(i - 108, playerhead);
            id.remove(0);
            reported.remove(0);
            reporter.remove(0);
            grund.remove(0);
            server.remove(0);
            status.remove(0);
            ++i;
        }
        while (i <= 179 && !id.isEmpty()) {
            final ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1);
            playerhead.setDurability((short) 3);
            final SkullMeta playerheadmeta = (SkullMeta) playerhead.getItemMeta();
            playerheadmeta.setOwner(reported.get(0));
            playerheadmeta.setDisplayName("§a" + reported.get(0));
            final ArrayList<String> playerheadlore = new ArrayList<>();
            playerheadlore.add("§7Reporter: §e" + reporter.get(0));
            playerheadlore.add("§7Grund: §e" + grund.get(0));
            playerheadlore.add("§7Server: §e" + server.get(0));
            playerheadlore.add("§7ReportID: §e" + id.get(0));
            playerheadlore.add("§7Status: §e" + ChatColor.translateAlternateColorCodes('&', status.get(0)));
            playerheadmeta.setLore(playerheadlore);
            playerhead.setItemMeta(playerheadmeta);
            inv5.setItem(i - 144, playerhead);
            id.remove(0);
            reported.remove(0);
            reporter.remove(0);
            grund.remove(0);
            server.remove(0);
            status.remove(0);
            ++i;
        }
        inv.setItem(36, line);
        inv.setItem(37, line);
        inv.setItem(38, line);
        inv.setItem(39, line);
        inv.setItem(40, line);
        inv.setItem(41, line);
        inv.setItem(42, line);
        inv.setItem(43, line);
        inv.setItem(44, line);
        inv.setItem(49, p);
        inv.setItem(50, pf);
        inv.setItem(53, exit);
        inv2.setItem(36, line);
        inv2.setItem(37, line);
        inv2.setItem(38, line);
        inv2.setItem(39, line);
        inv2.setItem(40, line);
        inv2.setItem(41, line);
        inv2.setItem(42, line);
        inv2.setItem(43, line);
        inv2.setItem(44, line);
        inv2.setItem(49, p2);
        inv2.setItem(50, pf);
        inv2.setItem(48, pb);
        inv2.setItem(53, exit);
        inv3.setItem(36, line);
        inv3.setItem(37, line);
        inv3.setItem(38, line);
        inv3.setItem(39, line);
        inv3.setItem(40, line);
        inv3.setItem(41, line);
        inv3.setItem(42, line);
        inv3.setItem(43, line);
        inv3.setItem(44, line);
        inv3.setItem(49, p3);
        inv3.setItem(50, pf);
        inv3.setItem(48, pb);
        inv3.setItem(53, exit);
        inv4.setItem(36, line);
        inv4.setItem(37, line);
        inv4.setItem(38, line);
        inv4.setItem(39, line);
        inv4.setItem(40, line);
        inv4.setItem(41, line);
        inv4.setItem(42, line);
        inv4.setItem(43, line);
        inv4.setItem(44, line);
        inv4.setItem(49, p4);
        inv4.setItem(50, pf);
        inv4.setItem(48, pb);
        inv4.setItem(53, exit);
        inv5.setItem(36, line);
        inv5.setItem(37, line);
        inv5.setItem(38, line);
        inv5.setItem(39, line);
        inv5.setItem(40, line);
        inv5.setItem(41, line);
        inv5.setItem(42, line);
        inv5.setItem(43, line);
        inv5.setItem(44, line);
        inv5.setItem(49, p5);
        inv5.setItem(48, pb);
        inv5.setItem(53, exit);
        if (repi == 1) {
            player.openInventory(inv);
        } else if (repi == 2) {
            player.openInventory(inv2);
        } else if (repi == 3) {
            player.openInventory(inv3);
        } else if (repi == 4) {
            player.openInventory(inv4);
        } else if (repi == 5) {
            player.openInventory(inv5);
        }
    }

    public static void openReportMenu(final Player p, final Integer id, final Boolean whichmenu) {
        final Inventory inv = Bukkit.createInventory(null, 54, "§eReport: §a" + id);
        final ItemStack exit = new ItemStack(Material.BARRIER, 1);
        final ItemMeta exitmeta = exit.getItemMeta();
        exitmeta.setDisplayName("§4Schließen");
        exit.setItemMeta(exitmeta);
        final ItemStack back = new ItemStack(Material.ARROW, 1);
        final ItemMeta backmeta = back.getItemMeta();
        backmeta.setDisplayName("§cZurück");
        final ArrayList<String> backlore = new ArrayList<>();
        backlore.add("§7Geht eine Seite zurück!");
        backmeta.setLore(backlore);
        back.setItemMeta(backmeta);
        final ItemStack Grund = new ItemStack(Material.BOOK, 1);
        final ItemMeta Grundmeta = Grund.getItemMeta();
        Grundmeta.setDisplayName("§5Grund:");
        final ArrayList<String> Grundlore = new ArrayList<>();
        Grundlore.add("§e" + ReportsManager.getReport(id, "Grund"));
        Grundmeta.setLore(Grundlore);
        Grund.setItemMeta(Grundmeta);
        final ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1);
        playerhead.setDurability((short) 3);
        final SkullMeta playerheadmeta = (SkullMeta) playerhead.getItemMeta();
        final ArrayList<String> playerlore = new ArrayList<>();
        playerlore.add("§c" + ReportsManager.getReport(id, "Reportedname"));
        playerheadmeta.setLore(playerlore);
        playerheadmeta.setOwner(ReportsManager.getReport(id, "Reportedname"));
        playerheadmeta.setDisplayName("§cBeschuldigter");
        playerhead.setItemMeta(playerheadmeta);
        final ItemStack reporterhead = new ItemStack(Material.SKULL_ITEM, 1);
        reporterhead.setDurability((short) 3);
        final SkullMeta reporterheadmeta = (SkullMeta) playerhead.getItemMeta();
        reporterheadmeta.setOwner(ReportsManager.getReport(id, "Reportername"));
        reporterheadmeta.setDisplayName("§aReporter");
        final ArrayList<String> reporterlore = new ArrayList<>();
        reporterlore.add("§a" + ReportsManager.getReport(id, "Reportername"));
        reporterheadmeta.setLore(reporterlore);
        reporterhead.setItemMeta(reporterheadmeta);
        final ItemStack Server = new ItemStack(Material.COMMAND, 1);
        final ItemMeta Servermeta = Server.getItemMeta();
        Servermeta.setDisplayName("§6Server:");
        final ArrayList<String> Serverlore = new ArrayList<>();
        Serverlore.add("§e" + ReportsManager.getReport(id, "Server"));
        Servermeta.setLore(Serverlore);
        Server.setItemMeta(Servermeta);
        final ItemStack Find = new ItemStack(Material.ENDER_PEARL, 1);
        final ItemMeta Findmeta = Find.getItemMeta();
        Findmeta.setDisplayName("§5Zum Reporter");
        final ArrayList<String> Findlore = new ArrayList<>();
        Findlore.add("§7Verschiebt dich auf den Server des Reporters.");
        Findmeta.setLore(Findlore);
        Find.setItemMeta(Findmeta);
        final ItemStack Find2 = new ItemStack(Material.ENDER_PEARL, 1);
        final ItemMeta Find2meta = Find2.getItemMeta();
        Find2meta.setDisplayName("§5Zum Beschuldigten");
        final ArrayList<String> Find2lore = new ArrayList<>();
        Find2lore.add("§7Verschiebt dich auf den Server des Beschuldigten.");
        Find2meta.setLore(Find2lore);
        Find2.setItemMeta(Find2meta);
        final ItemStack Delete = new ItemStack(Material.REDSTONE_BLOCK, 1);
        final ItemMeta Deletemeta = Delete.getItemMeta();
        Deletemeta.setDisplayName("§4Löschen");
        final ArrayList<String> Deletelore = new ArrayList<>();
        Deletelore.add("§7Löcht den Report!");
        Deletemeta.setLore(Deletelore);
        Delete.setItemMeta(Deletemeta);
        final ItemStack Status = new ItemStack(Material.WATCH, 1);
        final ItemMeta Statusmeta = Status.getItemMeta();
        Statusmeta.setDisplayName("§9Status:");
        final ArrayList<String> Statuslore = new ArrayList<>();
        final String status = ReportsManager.getReport(id, "Status");
        Statuslore.add(ChatColor.translateAlternateColorCodes('&', status));
        Statusmeta.setLore(Statuslore);
        Status.setItemMeta(Statusmeta);
        final ItemStack SetStatus = new ItemStack(Material.NAME_TAG, 1);
        final ItemMeta SetStatusmeta = Status.getItemMeta();
        SetStatusmeta.setDisplayName("§9Status setzen");
        final ArrayList<String> SetStatuslore = new ArrayList<>();
        SetStatuslore.add("§7Ändert den Status!");
        SetStatusmeta.setLore(SetStatuslore);
        SetStatus.setItemMeta(SetStatusmeta);
        final ItemStack ID = new ItemStack(Material.NAME_TAG, 1);
        final ItemMeta IDmeta = ID.getItemMeta();
        IDmeta.setDisplayName("§eReportID: §6" + id);
        final ArrayList<String> IDlore = new ArrayList<>();
        if (whichmenu) {
            IDlore.add("§cPlayerReports");
        } else {
            IDlore.add("§cAllReports");
        }
        IDmeta.setLore(IDlore);
        ID.setItemMeta(IDmeta);
        inv.setItem(1, Find);
        inv.setItem(7, Find2);
        inv.setItem(3, reporterhead);
        inv.setItem(4, ID);
        inv.setItem(5, playerhead);
        inv.setItem(20, Server);
        inv.setItem(22, Status);
        inv.setItem(24, Grund);
        inv.setItem(31, SetStatus);
        inv.setItem(45, back);
        inv.setItem(49, Delete);
        inv.setItem(53, exit);
        p.openInventory(inv);
    }

    public static void openPlayerReportsInv(final Player player, final String target, final Integer repi) {
        final Inventory inv = Bukkit.createInventory(null, 54, "§7Player§cReports");
        final Inventory inv2 = Bukkit.createInventory(null, 54, "§7Player§cReports");
        final Inventory inv3 = Bukkit.createInventory(null, 54, "§7Player§cReports");
        final Inventory inv4 = Bukkit.createInventory(null, 54, "§7Player§cReports");
        final Inventory inv5 = Bukkit.createInventory(null, 54, "§7Player§cReports");
        final ItemStack exit = new ItemStack(Material.BARRIER, 1);
        final ItemMeta exitmeta = exit.getItemMeta();
        exitmeta.setDisplayName("§4Schließen");
        exit.setItemMeta(exitmeta);
        final ItemStack back = new ItemStack(Material.ARROW, 1);
        final ItemMeta backmeta = back.getItemMeta();
        backmeta.setDisplayName("§cZurück");
        final ArrayList<String> backlore = new ArrayList<>();
        backlore.add("§7Geht eine Seite zurück!");
        backmeta.setLore(backlore);
        back.setItemMeta(backmeta);
        final ItemStack pf = new ItemStack(Material.PAPER, 1);
        final ItemMeta pfmeta = pf.getItemMeta();
        pfmeta.setDisplayName("§aNächste Seite");
        final ArrayList<String> pflore = new ArrayList<>();
        pflore.add("§7Geht eine Seite nach vorne!");
        pfmeta.setLore(pflore);
        pf.setItemMeta(pfmeta);
        final ItemStack pb = new ItemStack(Material.PAPER, 1);
        final ItemMeta pbmeta = pb.getItemMeta();
        pbmeta.setDisplayName("§cVorherige Seite");
        final ArrayList<String> pblore = new ArrayList<>();
        pblore.add("§7Geht eine Seite zurück!");
        pbmeta.setLore(pblore);
        pb.setItemMeta(pbmeta);
        final ItemStack p = new ItemStack(Material.BOOK, 1);
        final ItemMeta pmeta = p.getItemMeta();
        pmeta.setDisplayName("§6Aktuelle Seite");
        final ArrayList<String> plore = new ArrayList<>();
        plore.add("§7Aktuelle Seite: §e1");
        pmeta.setLore(plore);
        p.setItemMeta(pmeta);
        final ItemStack p2 = new ItemStack(Material.BOOK, 2);
        final ItemMeta p2meta = p2.getItemMeta();
        p2meta.setDisplayName("§6Aktuelle Seite");
        final ArrayList<String> p2lore = new ArrayList<>();
        p2lore.add("§7Aktuelle Seite: §e2");
        p2meta.setLore(p2lore);
        p2.setItemMeta(p2meta);
        final ItemStack p3 = new ItemStack(Material.BOOK, 3);
        final ItemMeta p3meta = p3.getItemMeta();
        p3meta.setDisplayName("§6Aktuelle Seite");
        final ArrayList<String> p3lore = new ArrayList<>();
        p3lore.add("§7Aktuelle Seite: §e3");
        p3meta.setLore(p3lore);
        p3.setItemMeta(p3meta);
        final ItemStack p4 = new ItemStack(Material.BOOK, 4);
        final ItemMeta p4meta = p4.getItemMeta();
        p4meta.setDisplayName("§6Aktuelle Seite");
        final ArrayList<String> p4lore = new ArrayList<>();
        p4lore.add("§7Aktuelle Seite: §e4");
        p4meta.setLore(p4lore);
        p4.setItemMeta(p4meta);
        final ItemStack p5 = new ItemStack(Material.BOOK, 5);
        final ItemMeta p5meta = p5.getItemMeta();
        p5meta.setDisplayName("§6Aktuelle Seite");
        final ArrayList<String> p5lore = new ArrayList<>();
        p5lore.add("§7Aktuelle Seite: §e5");
        p5meta.setLore(p5lore);
        p5.setItemMeta(p5meta);
        final ItemStack line = new ItemStack(Material.IRON_FENCE, 1);
        final ItemMeta linemeta = line.getItemMeta();
        linemeta.setDisplayName(" ");
        line.setItemMeta(linemeta);
        final ItemStack targethead = new ItemStack(Material.SKULL_ITEM, 1);
        targethead.setDurability((short) 3);
        final SkullMeta targetheadmeta = (SkullMeta) targethead.getItemMeta();
        targetheadmeta.setOwner(target);
        targetheadmeta.setDisplayName("§c" + target);
        targethead.setItemMeta(targetheadmeta);
        final ArrayList<Integer> id = ReportsManager.getUserIDs(target);
        final ArrayList<String> reported = ReportsManager.getUserReportLists("Reportedname", target);
        final ArrayList<String> reporter = ReportsManager.getUserReportLists("Reportername", target);
        final ArrayList<String> grund = ReportsManager.getUserReportLists("Grund", target);
        final ArrayList<String> server = ReportsManager.getUserReportLists("Server", target);
        final ArrayList<String> status = ReportsManager.getUserReportLists("Status", target);
        if (id.isEmpty()) {
            player.sendMessage(Main.getInstance().prefix + "Der Spieler §c§l" + target + "§7 wurde nicht Reportet!");
            return;
        }
        int i;
        for (i = 0; i <= 35; ++i) {
            if (id.isEmpty()) {
                break;
            }
            final ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1);
            playerhead.setDurability((short) 3);
            final SkullMeta playerheadmeta = (SkullMeta) playerhead.getItemMeta();
            playerheadmeta.setOwner(reported.get(0));
            playerheadmeta.setDisplayName("§a" + reported.get(0));
            final ArrayList<String> playerheadlore = new ArrayList<>();
            playerheadlore.add("§7Reporter: §e" + reporter.get(0));
            playerheadlore.add("§7Grund: §e" + grund.get(0));
            playerheadlore.add("§7Server: §e" + server.get(0));
            playerheadlore.add("§7ReportID: §e" + id.get(0));
            playerheadlore.add("§7Status: §e" + ChatColor.translateAlternateColorCodes('&', status.get(0)));
            playerheadmeta.setLore(playerheadlore);
            playerhead.setItemMeta(playerheadmeta);
            inv.setItem(i, playerhead);
            id.remove(0);
            reported.remove(0);
            reporter.remove(0);
            grund.remove(0);
            server.remove(0);
            status.remove(0);
        }
        while (i <= 71) {
            if (id.isEmpty()) {
                break;
            }
            final ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1);
            playerhead.setDurability((short) 3);
            final SkullMeta playerheadmeta = (SkullMeta) playerhead.getItemMeta();
            playerheadmeta.setOwner(reported.get(0));
            playerheadmeta.setDisplayName("§a" + reported.get(0));
            final ArrayList<String> playerheadlore = new ArrayList<>();
            playerheadlore.add("§7Reporter: §e" + reporter.get(0));
            playerheadlore.add("§7Grund: §e" + grund.get(0));
            playerheadlore.add("§7Server: §e" + server.get(0));
            playerheadlore.add("§7ReportID: §e" + id.get(0));
            playerheadlore.add("§7Status: §e" + ChatColor.translateAlternateColorCodes('&', status.get(0)));
            playerheadmeta.setLore(playerheadlore);
            playerhead.setItemMeta(playerheadmeta);
            inv2.setItem(i - 36, playerhead);
            id.remove(0);
            reported.remove(0);
            reporter.remove(0);
            grund.remove(0);
            server.remove(0);
            ++i;
        }
        while (i <= 107) {
            if (id.isEmpty()) {
                break;
            }
            final ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1);
            playerhead.setDurability((short) 3);
            final SkullMeta playerheadmeta = (SkullMeta) playerhead.getItemMeta();
            playerheadmeta.setOwner(reported.get(0));
            playerheadmeta.setDisplayName("§a" + reported.get(0));
            final ArrayList<String> playerheadlore = new ArrayList<>();
            playerheadlore.add("§7Reporter: §e" + reporter.get(0));
            playerheadlore.add("§7Grund: §e" + grund.get(0));
            playerheadlore.add("§7Server: §e" + server.get(0));
            playerheadlore.add("§7ReportID: §e" + id.get(0));
            playerheadlore.add("§7Status: §e" + ChatColor.translateAlternateColorCodes('&', status.get(0)));
            playerheadmeta.setLore(playerheadlore);
            playerhead.setItemMeta(playerheadmeta);
            inv3.setItem(i - 72, playerhead);
            id.remove(0);
            reported.remove(0);
            reporter.remove(0);
            grund.remove(0);
            server.remove(0);
            status.remove(0);
            ++i;
        }
        while (i <= 143) {
            if (id.isEmpty()) {
                break;
            }
            final ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1);
            playerhead.setDurability((short) 3);
            final SkullMeta playerheadmeta = (SkullMeta) playerhead.getItemMeta();
            playerheadmeta.setOwner(reported.get(0));
            playerheadmeta.setDisplayName("§a" + reported.get(0));
            final ArrayList<String> playerheadlore = new ArrayList<>();
            playerheadlore.add("§7Reporter: §e" + reporter.get(0));
            playerheadlore.add("§7Grund: §e" + grund.get(0));
            playerheadlore.add("§7Server: §e" + server.get(0));
            playerheadlore.add("§7ReportID: §e" + id.get(0));
            playerheadlore.add("§7Status: §e" + ChatColor.translateAlternateColorCodes('&', status.get(0)));
            playerheadmeta.setLore(playerheadlore);
            playerhead.setItemMeta(playerheadmeta);
            inv4.setItem(i - 108, playerhead);
            id.remove(0);
            reported.remove(0);
            reporter.remove(0);
            grund.remove(0);
            server.remove(0);
            status.remove(0);
            ++i;
        }
        while (i <= 179 && !id.isEmpty()) {
            final ItemStack playerhead = new ItemStack(Material.SKULL_ITEM, 1);
            playerhead.setDurability((short) 3);
            final SkullMeta playerheadmeta = (SkullMeta) playerhead.getItemMeta();
            playerheadmeta.setOwner(reported.get(0));
            playerheadmeta.setDisplayName("§a" + reported.get(0));
            final ArrayList<String> playerheadlore = new ArrayList<>();
            playerheadlore.add("§7Reporter: §e" + reporter.get(0));
            playerheadlore.add("§7Grund: §e" + grund.get(0));
            playerheadlore.add("§7Server: §e" + server.get(0));
            playerheadlore.add("§7ReportID: §e" + id.get(0));
            playerheadlore.add("§7Status: §e" + ChatColor.translateAlternateColorCodes('&', status.get(0)));
            playerheadmeta.setLore(playerheadlore);
            playerhead.setItemMeta(playerheadmeta);
            inv5.setItem(i - 144, playerhead);
            id.remove(0);
            reported.remove(0);
            reporter.remove(0);
            grund.remove(0);
            server.remove(0);
            status.remove(0);
            ++i;
        }
        inv.setItem(36, line);
        inv.setItem(37, line);
        inv.setItem(38, line);
        inv.setItem(39, line);
        inv.setItem(40, line);
        inv.setItem(41, line);
        inv.setItem(42, line);
        inv.setItem(43, line);
        inv.setItem(44, line);
        inv.setItem(45, targethead);
        inv.setItem(49, p);
        inv.setItem(50, pf);
        inv.setItem(53, exit);
        inv2.setItem(36, line);
        inv2.setItem(37, line);
        inv2.setItem(38, line);
        inv2.setItem(39, line);
        inv2.setItem(40, line);
        inv2.setItem(41, line);
        inv2.setItem(42, line);
        inv2.setItem(43, line);
        inv2.setItem(44, line);
        inv2.setItem(45, targethead);
        inv2.setItem(49, p2);
        inv2.setItem(50, pf);
        inv2.setItem(48, pb);
        inv2.setItem(53, exit);
        inv3.setItem(36, line);
        inv3.setItem(37, line);
        inv3.setItem(38, line);
        inv3.setItem(39, line);
        inv3.setItem(40, line);
        inv3.setItem(41, line);
        inv3.setItem(42, line);
        inv3.setItem(43, line);
        inv3.setItem(44, line);
        inv3.setItem(45, targethead);
        inv3.setItem(49, p3);
        inv3.setItem(50, pf);
        inv3.setItem(48, pb);
        inv3.setItem(53, exit);
        inv4.setItem(36, line);
        inv4.setItem(37, line);
        inv4.setItem(38, line);
        inv4.setItem(39, line);
        inv4.setItem(40, line);
        inv4.setItem(41, line);
        inv4.setItem(42, line);
        inv4.setItem(43, line);
        inv4.setItem(44, line);
        inv4.setItem(45, targethead);
        inv4.setItem(49, p4);
        inv4.setItem(50, pf);
        inv4.setItem(48, pb);
        inv4.setItem(53, exit);
        inv5.setItem(36, line);
        inv5.setItem(37, line);
        inv5.setItem(38, line);
        inv5.setItem(39, line);
        inv5.setItem(40, line);
        inv5.setItem(41, line);
        inv5.setItem(42, line);
        inv5.setItem(43, line);
        inv5.setItem(44, line);
        inv5.setItem(45, targethead);
        inv5.setItem(49, p5);
        inv5.setItem(48, pb);
        inv5.setItem(53, exit);
        if (repi == 1) {
            player.openInventory(inv);
        } else if (repi == 2) {
            player.openInventory(inv2);
        } else if (repi == 3) {
            player.openInventory(inv3);
        } else if (repi == 4) {
            player.openInventory(inv4);
        } else if (repi == 5) {
            player.openInventory(inv5);
        }
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(this.plugin.prefix + "Du musst ein Spieler sein um diesen Befehl zu nutzen!");
            return true;
        }
        final Player p = (Player) sender;
        if (args.length > 0) {
            if (args.length == 1) {
                openPlayerReportsInv(p, args[0], 1);
            }
        } else if (p.hasPermission("bs.reports")) {
            openReportsInv(p, 1);
        } else {
            p.sendMessage(this.plugin.prefix + "Dazu hast du keine Rechte!");
            p.sendMessage(this.plugin.prefix + "Wolltest du einen Spieler reporten? §c/report [Spieler] [Grund]");
        }
        return true;
    }
}
