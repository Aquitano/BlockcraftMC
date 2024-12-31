package de.Blockcraft.BungeeSystem.Util;

import de.Blockcraft.BungeeSystem.Data;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;

import java.time.Duration;
import java.util.List;

public class BanManager {
    private static final Configuration cfg = Files.BanConfig;

    public static boolean exists(String playerName) {
        String uuid = PlayerUtil.getUUID(playerName);
        return uuid != null && cfg.get("Spieler." + uuid) != null;
    }

    public static void createPlayer(String playerName) {
        if (!exists(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            if (uuid == null) {
                System.out.println("UUID not found for player: " + playerName);
                return;
            }
            cfg.set("Spieler." + uuid + ".Spielername", playerName);
            cfg.set("Spieler." + uuid + ".Ban", false);
            cfg.set("Spieler." + uuid + ".Grund", "");
            cfg.set("Spieler." + uuid + ".von", "");
            cfg.set("Spieler." + uuid + ".Ende", 0L);
            Files.saveBanFile();
        }
    }

    public static boolean isBanned(String playerName) {
        if (exists(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            return cfg.getBoolean("Spieler." + uuid + ".Ban", false);
        }
        return false;
    }

    public static void ban(String playerName, String reason, String issuer, long seconds) {
        if (!isBanned(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            if (uuid == null) {
                System.out.println("UUID not found for player: " + playerName);
                return;
            }

            long currentTime = System.currentTimeMillis();
            long endTime = (seconds == -1) ? -1 : currentTime + (seconds * 1000);

            cfg.set("Spieler." + uuid + ".Spielername", playerName);
            cfg.set("Spieler." + uuid + ".Ban", true);
            cfg.set("Spieler." + uuid + ".Grund", reason);
            cfg.set("Spieler." + uuid + ".von", issuer);
            cfg.set("Spieler." + uuid + ".Ende", endTime);

            List<String> bannedPlayers = cfg.getStringList("GebannteSpieler");
            bannedPlayers.add(playerName);
            cfg.set("GebannteSpieler", bannedPlayers);

            Files.saveBanFile();

            ProxiedPlayer target = BungeeCord.getInstance().getPlayer(playerName);
            if (target != null) {
                target.disconnect(getBannedMessage(playerName));
            }

            notifyAdmins(playerName, reason, issuer, getRemainingTime(playerName));
        }
    }

    public static void unBan(String playerName, String issuer) {
        if (isBanned(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            if (uuid == null) {
                System.out.println("UUID not found for player: " + playerName);
                return;
            }

            cfg.set("Spieler." + uuid + ".Ban", false);
            cfg.set("Spieler." + uuid + ".Grund", "");
            cfg.set("Spieler." + uuid + ".von", "");
            cfg.set("Spieler." + uuid + ".Ende", 0L);

            List<String> bannedPlayers = cfg.getStringList("GebannteSpieler");
            bannedPlayers.remove(playerName);
            cfg.set("GebannteSpieler", bannedPlayers);

            Files.saveBanFile();

            notifyAdminsUnban(playerName, issuer);
        }
    }

    public static List<String> getBannedPlayers() {
        return cfg.getStringList("GebannteSpieler");
    }

    public static String getReason(String playerName) {
        if (isBanned(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            return cfg.getString("Spieler." + uuid + ".Grund", "");
        }
        return "";
    }

    public static String getWhoBanned(String playerName) {
        if (isBanned(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            return cfg.getString("Spieler." + uuid + ".von", "");
        }
        return "";
    }

    public static void addToList(String playerName, String reason) {
        String uuid = PlayerUtil.getUUID(playerName);
        if (uuid != null) {
            cfg.set("BereitsGebannt." + uuid, "true");
            cfg.set("BanGrund." + uuid, reason);
            Files.saveBanFile();
        }
    }

    public static boolean isAlreadyBanned(String playerName) {
        String uuid = PlayerUtil.getUUID(playerName);
        if (uuid == null) {
            return false;
        }
        String banned = cfg.getString("BereitsGebannt." + uuid, "false");
        if ("true".equalsIgnoreCase(banned)) {
            return true;
        } else {
            addToList(playerName, "Grundi");
            return false;
        }
    }

    public static String getReasonFromLast(String playerName) {
        String uuid = PlayerUtil.getUUID(playerName);
        if (uuid != null) {
            return cfg.getString("BanGrund." + uuid, "Unbekannt");
        }
        return "Unbekannt";
    }

    public static long getEnd(String playerName) {
        if (isBanned(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            return cfg.getLong("Spieler." + uuid + ".Ende", -1L);
        }
        return -1L;
    }

    public static String getRemainingTime(String playerName) {
        if (isBanned(playerName)) {
            long endTime = getEnd(playerName);
            if (endTime == -1) {
                return "\u00a74Permanent";
            }

            long currentTime = System.currentTimeMillis();
            long difference = endTime - currentTime;

            System.out.println("Difference: " + difference);
            System.out.println("Current Time: " + currentTime);
            System.out.println("End Time: " + endTime);

            return formatDuration(difference);
        }
        return "Nicht Gebannt.";
    }

    private static String formatDuration(long millis) {
        if (millis < 0) {
            return "Abgelaufen.";
        }

        Duration duration = Duration.ofMillis(millis);

        long days = duration.toDays();
        duration = duration.minusDays(days);
        long hours = duration.toHours();
        duration = duration.minusHours(hours);
        long minutes = duration.toMinutes();
        duration = duration.minusMinutes(minutes);
        long seconds = duration.getSeconds();

        System.out.println("Formatierung: " + days + " Tage, " + hours + " Stunden, " + minutes + " Minuten, " + seconds + " Sekunden.");

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(" Tag");
            if (days > 1) sb.append("e");
            sb.append(", ");
        }
        if (hours > 0) {
            sb.append(hours).append(" Stunde");
            if (hours > 1) sb.append("n");
            sb.append(", ");
        }
        if (minutes > 0) {
            sb.append(minutes).append(" Minute");
            if (minutes > 1) sb.append("n");
            sb.append(", ");
        }
        sb.append(seconds).append(" Sekunde");
        if (seconds != 1) sb.append("n");

        return sb.toString();
    }

    public static String getBannedMessage(String playerName) {
        if (isBanned(playerName)) {
            String ip = Data.IP;
            String website = Data.WEBSITE;
            String ts = Data.TS;
            String reason = getReason(playerName);
            String remainingTime = getRemainingTime(playerName);
            System.out.println("Remaining Time: " + remainingTime);
            return (getEnd(playerName) != -1)
                    ? String.format("\u00a77Du wurdest vom \u00a73%s Netzwerk \u00a77gebannt.\n \u00a77Grund: \u00a7a%s \n \n \u00a77Verbleibende Zeit: \u00a7a\n%s\n \n \u00a77Unrechter Ban? Stelle einen \u00a73Entbannungsantrag\u00a77 im Forum:\n \u00a73%s/Forum \n \u00a77oder auf dem TeamSpeak:\n \u00a73%s",
                    ip, reason, remainingTime, website, ts)
                    : String.format("\u00a77Du wurdest vom \u00a73%s Netzwerk \u00a77gebannt.\n \u00a77Grund: \u00a7a%s \n \n \u00a77Verbleibende Zeit: \u00a7a\n%s\n \n \u00a77Unrechter Ban? Stelle einen \u00a73Entbannungsantrag\u00a77 im Forum:\n \u00a73%s \n \u00a77oder auf dem TeamSpeak:\n \u00a73%s",
                    ip, reason, remainingTime, website, ts);
        }
        return "";
    }

    private static void notifyAdmins(String playerName, String reason, String issuer, String remainingTime) {
        for (ProxiedPlayer admin : BungeeCord.getInstance().getPlayers()) {
            if (admin.hasPermission("bs.kick")) {
                admin.sendMessage(Data.STRAFE_PREFIX + "\u00a7c" + playerName + "\u00a77 wurde vom \u00a7cNetzwerk \u00a77gesperrt.");
                admin.sendMessage(Data.STRAFE_PREFIX + "\u00a77Grund: \u00a7c" + reason);
                admin.sendMessage(Data.STRAFE_PREFIX + "\u00a77Von: \u00a7c" + issuer);
                admin.sendMessage(Data.STRAFE_PREFIX + "\u00a77Länge: \u00a7c" + remainingTime);
            }
        }
    }

    private static void notifyAdminsUnban(String playerName, String issuer) {
        for (ProxiedPlayer admin : BungeeCord.getInstance().getPlayers()) {
            if (admin.hasPermission("bs.kick")) {
                admin.sendMessage(Data.STRAFE_PREFIX + "\u00a77Der Spieler \u00a7b" + playerName + " \u00a77wurde von \u00a7c" + issuer + " \u00a77entbannt.");
            }
        }
    }
}
