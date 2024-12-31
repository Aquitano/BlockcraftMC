package de.Blockcraft.BungeeSystem;

import de.Blockcraft.Party.PartyCMD;
import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;

public class Data {
    public static final Functions FUNCTIONS = new Data.Functions();

    public static final String PREFIX = ChatColor.GOLD + "Blockcraft " + ChatColor.DARK_GRAY + "» " + ChatColor.RESET;
    public static final String NO_PERM = "\u00a7cDu hast keine Rechte dafür!";
    public static final String STRAFE_PREFIX = "\u00a74\u00a7lStrafe \u00a78» "; // Strafe-Command Prefix

    public static final String PLAYER_ONLY = "\u00a7cNur ein Spieler kann diesen Befehl verwenden!";
    public static final String WEBSITE = "https://BlockcraftMC.net";
    public static final String SHOP = "https://shop.BlockcraftMC.net";
    public static final String TS = "ts.BlockcraftMC.net";
    public static final String DISCORD = "https://discord.gg/kh3xaQ3";
    public static final String IP = "BlockcraftMC.net";
    public static final String BLOCKCAST_PREFIX = "BlockCast";
    public static final String PARTY_CHAT = "\u00a7c%player% \u00a7f: \u00a77%msg%";
    public static final List<String> UNBANNABLE = new ArrayList<>();

    /**
     * Server names
     */

    public static final String lobbyDef = "lobby"; // Standard Lobby Servername
    public static String BWdef = "BW-1"; // Standard Bedwars Server
    public static final String BWpref = "BW"; // Bedwars Prefix
    public static String SWdef = "SW-1"; // Standard Skywars Server
    public static final String SWpref = "SW"; // Skywars Prefix
    public static String KFFAdef = "KFFA-1"; // Standard KnockbackFFA Server
    public static final String KFFApref = "KFFA"; // KnockbackFFA Prefix
    public static final String SoupPvPdef = "SoupPvP"; // SoupPvP Servername
    public static final String BauServerdef = "BauServer"; // BauServer Servername

    public static final String RushDef = "EM"; // TradeWars Prefix

    public static void initUnbannable() {
        UNBANNABLE.add("Aquitano");
    }

    public static class Functions {
        public Functions() {
        }

        public String colorFormat(String s) { // Die Alternative: ChatColor.translateAlternateColorCodes('&',
            // "Nachricht");
            int amount = 22;

            CharSequence[] chr = {"&0", "&1", "&2", "&3", "&4", "&5", "&6", "&7", "&8", "&9", "&a", "&b", "&c", "&d",
                    "&e", "&f", "&k", "&l", "&m", "&n", "&o", "&r"};
            CharSequence[] rep = {"\u00a70", "\u00a71", "\u00a72", "\u00a73", "\u00a74", "\u00a75", "\u00a76",
                    "\u00a77", "\u00a78", "\u00a79", "\u00a7a", "\u00a7b", "\u00a7c", "\u00a7d",
                    "\u00a7e", "\u00a7f", "\u00a7k", "\u00a7l", "\u00a7m", "\u00a7n", "\u00a7o", "\u00a7r"};

            for (int i = 0; i < amount; i++) {
                s = s.replace(chr[i], rep[i]);
            }

            return s;
        }

        public String timeFormat(long millis) {
            long t = millis / 1000;

            if (t < 60 && t >= 0) {
                return secTime(t);
            }

            if (t < 3600 && t >= 0) {
                return minTime(t);
            }

            if (t < 86400 && t >= 0) {
                return hrTime(t);
            }

            if (t < 604800 && t >= 0) {
                return dayTime(t);
            }

            if (t < 2592000 && t >= 0) {
                return wkTime(t);
            }

            if (t < 31104000 && t >= 0) {
                return monTime(t);
            }

            if (t > 31104000) {
                return yrTime(t);
            }

            return Long.toString(t);
        }

        private String secTime(long t) {
            if (t == 1) {
                return t + " Sekunde";
            }

            return t + " Sekunden";
        }

        private String minTime(long t) {
            long lw = t / 60;

            if (lw == 1) {
                return lw + " Minute " + secTime(0);
            }

            long rw = t % 60;

            return lw + " Minuten " + secTime(rw);
        }

        private String hrTime(long t) {
            long lw = t / 3600;

            if (lw == 1) {
                return lw + " Stunde " + minTime(0);
            }

            long rw = t % 3600;

            return lw + " Stunden " + minTime(rw);
        }

        private String dayTime(long t) {
            long lw = t / 86400;

            if (lw == 1) {
                return lw + " Tag " + hrTime(0);
            }

            long rw = t % 86400;

            return lw + " Tage " + hrTime(rw);
        }

        private String wkTime(long t) {
            long lw = t / 604800;

            if (lw == 1) {
                return lw + " Woche " + dayTime(0);
            }

            long rw = t % 604800;

            return lw + " Wochen " + dayTime(rw);
        }

        private String monTime(long t) {
            long lw = t / 2592000;

            if (lw == 1) {
                return lw + " Monat " + wkTime(0);
            }

            long rw = t % 2592000;
            return lw + " Monate " + wkTime(rw);
        }

        private String yrTime(long t) {
            long lw = t / 31104000;

            if (lw == 1) {
                return lw + " Jahr " + monTime(0);
            }

            long rw = t % 31104000;

            return lw + " Jahre " + monTime(rw);
        }

    }

}
