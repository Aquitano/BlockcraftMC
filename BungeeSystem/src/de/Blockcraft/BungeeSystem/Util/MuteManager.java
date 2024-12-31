package de.Blockcraft.BungeeSystem.Util;

import de.Blockcraft.BungeeSystem.Data;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.config.Configuration;

import java.util.List;

public class MuteManager {
    private static final Configuration cfg = Files.MuteConfig;

    public static boolean exists(String playerName) {
        String uuid = PlayerUtil.getUUID(playerName);
        return uuid != null && cfg.get("Players." + uuid) != null;
    }

    public static void createPlayer(String playerName) {
        if (!exists(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            if (uuid == null) {
                System.out.println("UUID not found for player: " + playerName);
                return;
            }
            cfg.set("Players." + uuid + ".Playername", playerName);
            cfg.set("Players." + uuid + ".Muted", false);
            cfg.set("Players." + uuid + ".Reason", "");
            cfg.set("Players." + uuid + ".By", "");
            cfg.set("Players." + uuid + ".End", 0L);
            Files.saveMuteFile();
        }
    }

    public static boolean isMuted(String playerName) {
        if (exists(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            return cfg.getBoolean("Players." + uuid + ".Muted", false);
        }
        return false;
    }

    public static void mute(String playerName, String reason, String issuer, int seconds) {
        if (!isMuted(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            if (uuid == null) {
                System.out.println("UUID not found for player: " + playerName);
                return;
            }

            long currentTime = System.currentTimeMillis();
            long endTime = (seconds == -1) ? -1L : currentTime + (seconds * 1000L);

            cfg.set("Players." + uuid + ".Playername", playerName);
            cfg.set("Players." + uuid + ".Muted", true);
            cfg.set("Players." + uuid + ".Reason", reason);
            cfg.set("Players." + uuid + ".By", issuer);
            cfg.set("Players." + uuid + ".End", endTime);

            List<String> mutedPlayers = cfg.getStringList("MutedPlayers");
            mutedPlayers.add(playerName);
            cfg.set("MutedPlayers", mutedPlayers);

            Files.saveMuteFile();

            ProxiedPlayer target = BungeeCord.getInstance().getPlayer(playerName);
            if (target != null) {
                target.disconnect(getMutedMessage(playerName));
            }

            notifyAdmins(playerName, reason, issuer, getRemainingTime(playerName));
        }
    }

    public static void unMute(String playerName, String issuer) {
        if (isMuted(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            if (uuid == null) {
                System.out.println("UUID not found for player: " + playerName);
                return;
            }

            cfg.set("Players." + uuid + ".Muted", false);
            cfg.set("Players." + uuid + ".Reason", "");
            cfg.set("Players." + uuid + ".By", "");
            cfg.set("Players." + uuid + ".End", 0L);

            List<String> mutedPlayers = cfg.getStringList("MutedPlayers");
            mutedPlayers.remove(playerName);
            cfg.set("MutedPlayers", mutedPlayers);

            Files.saveMuteFile();

            notifyAdminsUnmute(playerName, issuer);
        }
    }

    public static List<String> getMutedPlayers() {
        return cfg.getStringList("MutedPlayers");
    }

    public static String getReason(String playerName) {
        if (isMuted(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            return cfg.getString("Players." + uuid + ".Reason", "");
        }
        return "";
    }

    public static String getWhoMuted(String playerName) {
        if (isMuted(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            return cfg.getString("Players." + uuid + ".By", "");
        }
        return "";
    }

    public static long getEnd(String playerName) {
        if (isMuted(playerName)) {
            String uuid = PlayerUtil.getUUID(playerName);
            return cfg.getLong("Players." + uuid + ".End", -1L);
        }
        return -1L;
    }

    public static String getRemainingTime(String playerName) {
        if (isMuted(playerName)) {
            long endTime = getEnd(playerName);
            if (endTime == -1L) {
                return "\u00a74Permanent";
            }

            long currentTime = System.currentTimeMillis();
            long difference = endTime - currentTime;

            if (difference <= 0L) {
                return "Abgelaufen.";
            }

            return Data.FUNCTIONS.timeFormat(difference);
        }
        return "Nicht Gemuted.";
    }

    public static String getMutedMessage(String playerName) {
        if (isMuted(playerName)) {
            String ip = Data.IP;
            String website = Data.WEBSITE;
            String ts = Data.TS;
            String reason = getReason(playerName);
            String remainingTime = getRemainingTime(playerName);
            return (getEnd(playerName) != -1L)
                    ? String.format("\u00a77Du wurdest vom \u00a73%s Netzwerk \u00a77gemuted.\n \u00a77Grund: \u00a7a%s \n \n \u00a77Verbleibende Zeit: \u00a7a\n%s\n \n \u00a77Unrechter Mute? Stelle einen \u00a73Entmutungsantrag\u00a77 im Forum:\n \u00a73%s/Forum \n \u00a77oder auf dem TeamSpeak:\n \u00a73%s",
                    ip, reason, remainingTime, website, ts)
                    : String.format("\u00a77Du wurdest vom \u00a73%s Netzwerk \u00a77gemuted.\n \u00a77Grund: \u00a7a%s \n \n \u00a77Verbleibende Zeit: \u00a7a\n%s\n \n \u00a77Unrechter Mute? Stelle einen \u00a73Entmutungsantrag\u00a77 im Forum:\n \u00a73%s \n \u00a77oder auf dem TeamSpeak:\n \u00a73%s",
                    ip, reason, remainingTime, website, ts);
        }
        return "";
    }

    private static void notifyAdmins(String playerName, String reason, String issuer, String remainingTime) {
        for (ProxiedPlayer admin : BungeeCord.getInstance().getPlayers()) {
            if (admin.hasPermission("bs.team")) {
                admin.sendMessage(Data.PREFIX + "\u00a77Der Spieler \u00a7c" + playerName + " \u00a77wurde \u00a77gemuted.");
                admin.sendMessage(Data.PREFIX + "\u00a77Grund: \u00a7c" + reason);
                admin.sendMessage(Data.PREFIX + "\u00a77Von: \u00a7c" + issuer);
                admin.sendMessage(Data.PREFIX + "\u00a77Verbleibende Zeit: \u00a7c" + remainingTime);
            }
        }
    }

    private static void notifyAdminsUnmute(String playerName, String issuer) {
        for (ProxiedPlayer admin : BungeeCord.getInstance().getPlayers()) {
            if (admin.hasPermission("bs.team")) {
                admin.sendMessage(Data.PREFIX + "Der Spieler \u00a7c" + playerName + " \u00a77wurde von \u00a7c" + issuer + " \u00a77entmuted.");
            }
        }
    }

    public static void addToList(String playerName, String reason) {
        String uuid = PlayerUtil.getUUID(playerName);
        if (uuid != null) {
            cfg.set("BereitsGemuted." + uuid, "true");
            cfg.set("MuteGrund." + uuid, reason);
            Files.saveMuteFile();
        }
    }

    public static boolean isAlreadyMuted(String playerName) {
        String uuid = PlayerUtil.getUUID(playerName);
        if (uuid == null) {
            return false;
        }
        String muted = cfg.getString("BereitsGemuted." + uuid, "false");
        if ("true".equalsIgnoreCase(muted)) {
            return true;
        } else {
            addToList(playerName, "Grundi");
            return false;
        }
    }

    public static String getReasonFromLast(String playerName) {
        String uuid = PlayerUtil.getUUID(playerName);
        if (uuid != null) {
            return cfg.getString("MuteGrund." + uuid, "Unbekannt");
        }
        return "Unbekannt";
    }
}
