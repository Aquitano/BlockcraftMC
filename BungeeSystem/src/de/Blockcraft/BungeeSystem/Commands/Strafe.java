package de.Blockcraft.BungeeSystem.Commands;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.Util.BanManager;
import de.Blockcraft.BungeeSystem.Util.MuteManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Strafe extends Command {
    private static final Map<String, Punishment> punishments = new HashMap<>();

    static {
        punishments.put("1", new Punishment(PunishmentType.BAN, "Hacking Anti-Knockback", 16 * 60 * 60 * 24)); // 16 days
        punishments.put("2", new Punishment(PunishmentType.BAN, "Hacking KillAura", 20 * 60 * 60 * 24)); // 20 days
        punishments.put("3", new Punishment(PunishmentType.BAN, "Hacking Fly", 14 * 60 * 60 * 24)); // 14 days
        punishments.put("4", new Punishment(PunishmentType.BAN, "Hacking Autoclicker", 14 * 60 * 60 * 24)); // 14 days
        punishments.put("5", new Punishment(PunishmentType.BAN, "Hacking Speed", 12 * 60 * 60 * 24)); // 12 days
        punishments.put("6", new Punishment(PunishmentType.BAN, "Hacking Sonstiges", 21 * 60 * 60 * 24)); // 21 days
        punishments.put("7", new Punishment(PunishmentType.MUTE, "Beleidigungen Team", 3 * 60 * 60 * 24)); // 3 days
        punishments.put("8", new Punishment(PunishmentType.MUTE, "Beleidigungen Spieler", 2 * 60 * 60 * 24)); // 2 days
        punishments.put("9", new Punishment(PunishmentType.MUTE, "Beleidigungen Netzwerk", 60 * 60 * 24)); // 1 day
        punishments.put("10", new Punishment(PunishmentType.BAN, "Fehlverhalten Bugusing", 60 * 60 * 12)); // 12 hours
        punishments.put("11", new Punishment(PunishmentType.BAN, "Fehlverhalten Drohung", -1)); // Permanent
        punishments.put("12", new Punishment(PunishmentType.BAN, "Fehlverhalten Radikalismus", -1)); // Permanent
        punishments.put("13", new Punishment(PunishmentType.BAN, "Fehlverhalten Werbung", 20 * 60 * 60 * 24)); // 20 days
        punishments.put("14", new Punishment(PunishmentType.BAN, "Fehlverhalten Combat-Log", 60 * 60)); // 1 hour
        punishments.put("15", new Punishment(PunishmentType.BAN, "Fehlverhalten Teaming", 3601)); // ~1 hour
        punishments.put("16", new Punishment(PunishmentType.MUTE, "Oberflächliche Beleidigung", 1801)); // ~30 minutes
        punishments.put("17", new Punishment(PunishmentType.MUTE, "Respektloses Verhalten", 2701)); // ~45 minutes
        punishments.put("18", new Punishment(PunishmentType.BAN, "Report-Abuse", 3601)); // ~1 hour
        punishments.put("19", new Punishment(PunishmentType.MUTE, "Provokante Ausdrucksweise", 7201)); // ~2 hours
        punishments.put("20", new Punishment(PunishmentType.BAN, "Provokation (Allgemein)", 3601)); // ~1 hour
        punishments.put("21", new Punishment(PunishmentType.BAN, "Alt-Account", -1)); // Permanent
        punishments.put("22", new Punishment(PunishmentType.BAN, "Banumgehung", -1)); // Permanent
        punishments.put("23", new Punishment(PunishmentType.BAN, "Griefing", -1)); // Permanent
        punishments.put("24", new Punishment(PunishmentType.BAN, "Extrem", -1, "Extrem kommt zu stande, indem gegen sehr viele Regeln oft und stark verstoßen wird."));
    }

    public Strafe() {
        super("strafe");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("bs.ban")) {
            sender.sendMessage(Data.PREFIX + ChatColor.RED + "Nutze /ban");
            return;
        }

        if (args.length != 2) {
            displayPunishmentList(sender);
            return;
        }

        String targetName = args[0];
        String punishmentNumber = args[1];

        Punishment punishment = punishments.get(punishmentNumber);
        if (punishment == null) {
            sender.sendMessage(Data.STRAFE_PREFIX + ChatColor.RED + "Diese Nummer ist unbekannt.");
            return;
        }

        if (Data.UNBANNABLE.contains(targetName.toLowerCase())) {
            sender.sendMessage(Data.STRAFE_PREFIX + ChatColor.RED + "Du kannst diesen Spieler nicht bannen!");
            return;
        }

        if (BanManager.isBanned(targetName)) {
            sender.sendMessage(Data.STRAFE_PREFIX + ChatColor.RED + "Dieser Spieler wurde bereits bestraft. Seine Strafe wird nun überschrieben...");
        }

        applyPunishment(sender, targetName, punishment);
    }

    private void displayPunishmentList(CommandSender sender) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&c&lHack Strafen"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oHacks &8| &cAntiKnockback : &b1"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oHacks &8| &cKillAura : &b2"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oHacks &8| &cAutoclicker : &b3"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oHacks &8| &cFly : &b4"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oHacks &8| &cSpeed : &b5"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oHacks &8| &cSonstiges : &b6"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&a&lBeleidigungs Strafen"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oBeleidigungen &8| &cTeam : &b7"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oBeleidigungen &8| &cSpieler : &b8"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oBeleidigungen &8| &cNetzwerk : &b9"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&e&lSonstige Strafen"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oSonstiges &8| &cBugusing : &b10"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oSonstiges &8| &cDrohung : &b11"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oSonstiges &8| &cRadikalismus : &b12"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oSonstiges &8| &cWerbung : &b13"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oSonstiges &8| &cCombat-Log : &b14"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&3&lWeitere Strafen"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oWeiteres &8| &cTeaming : &b15"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oWeiteres &8| &cLeichte Beleidigung : &b16"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oWeiteres &8| &cRespektloses Verhalten: &b17"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oWeiteres &8| &cReport-Abuse: &b18"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oWeiteres &8| &cProvokation-Beleidigung: &b19"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oWeiteres &8| &cProvokation-Allgemein: &b20"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oWeiteres &8| &cAlt-Account: &b21"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oWeiteres &8| &cBanumgehung: &b22"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oWeiteres &8| &cGriefing: &b23"));
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                " &7&oWeiteres &8| &cExtrem : &b24"));
    }

    private void applyPunishment(CommandSender sender, String targetName, Punishment punishment) {
        String banID = generateBanID();

        switch (punishment.getType()) {
            case BAN -> BanManager.ban(targetName, punishment.getReason() + " #" + banID, sender.getName(), punishment.getDuration());
            case MUTE -> MuteManager.mute(targetName, punishment.getReason() + " #" + banID, sender.getName(), punishment.getDuration());
        }

        sender.sendMessage(Data.STRAFE_PREFIX + ChatColor.GREEN + "Du hast den Spieler erfolgreich bestraft.");
    }

    private String generateBanID() {
        StringBuilder banID = new StringBuilder();
        Random random = new Random();
        int previous = -1;

        for (int i = 0; i < 4; i++) {
            int digit = random.nextInt(10);
            while (digit == previous) {
                digit = random.nextInt(10);
            }
            banID.append(digit);
            previous = digit;
        }

        return banID.toString();
    }

    private enum PunishmentType {
        BAN,
        MUTE
    }

    private static class Punishment {
        private final PunishmentType type;
        private final String reason;
        private final long duration;

        public Punishment(PunishmentType type, String reason, long duration) {
            this.type = type;
            this.reason = reason;
            this.duration = duration;
        }

        public Punishment(PunishmentType type, String reason, long duration, String detailedReason) {
            this.type = type;
            this.reason = reason + " - " + detailedReason;
            this.duration = duration;
        }

        public PunishmentType getType() {
            return type;
        }

        public String getReason() {
            return reason;
        }

        public int getDuration() {
            return (int) duration;
        }
    }
}