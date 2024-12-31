package de.Blockcraft.BungeeSystem.Listener;

import de.Blockcraft.BungeeSystem.Data;
import de.Blockcraft.BungeeSystem.MOTD.Config;
import de.Blockcraft.BungeeSystem.Main;
import de.Blockcraft.BungeeSystem.Util.MuteManager;
import de.Blockcraft.BungeeSystem.Util.PlayerUtil;
import de.Blockcraft.HubAPI.Hub;
import de.Blockcraft.HubAPI.SelectLobby;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CListener implements Listener {
    private static final Set<String> spammingPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        final ProxiedPlayer player = event.getPlayer();

        if (ProxyServer.getInstance().getOnlineCount() > Config.Config.MaxPlayers
                && !player.hasPermission("bs.premiumlobby")) {
            String disconnectMessage = ChatColor.translateAlternateColorCodes('&',
                    Data.PREFIX +
                            "&7Das maximale &cSpielerlimit &7von &c" + Config.Config.MaxPlayers +
                            " &7ist erreicht.\n" +
                            "&c\n" +
                            "&7Nur noch &cSpieler &7mit dem &cPremiumRang &7können noch &cJoinen!\n" +
                            "&7Discord: &c" + Data.DISCORD);
            player.disconnect(disconnectMessage);
            return;
        }

        ProxyServer.getInstance().getScheduler().schedule(Main.instance, () -> {
            String uuid = PlayerUtil.getUUID(player.getName());
            if (uuid == null) {
                System.out.println("UUID not found for player: " + player.getName());
                return;
            }

            if (Main.playerConf.get("Spieler." + uuid) == null) {
                Main.playerConf.set("Spieler." + uuid + ".PlayerName", player.getName());
                Main.playerConf.set("Amount", Main.playerConf.getInt("Amount", 0) + 1);

                try {
                    ConfigurationProvider.getProvider(YamlConfiguration.class).save(Main.playerConf, Main.playerFile);
                } catch (IOException e) {
                    System.out.println("Failed to save player configuration for " + player.getName() + ": " + e.getMessage());
                    e.printStackTrace();
                }

                String broadcastMessage = ChatColor.translateAlternateColorCodes('&',
                        "&3&m-------------------------------------------\n" +
                                "\n" +
                                "\n" +
                                Data.PREFIX + ChatColor.YELLOW + player.getName() + ChatColor.GRAY + " ist neu auf " + ChatColor.BLUE + Data.IP + ChatColor.GRAY + "! " +
                                ChatColor.DARK_AQUA + "[#" + Main.playerConf.getInt("Amount") + "]\n" +
                                "\n" +
                                "\n" +
                                "&3&m-------------------------------------------");

                for (ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
                    all.sendMessage(broadcastMessage);
                }
            }

            if (!Objects.equals(player.getServer().getInfo().getName(), SelectLobby.select())) {
                Hub.send(player);
            }
        }, 2, TimeUnit.SECONDS);
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer player)) {
            return;
        }

        String message = event.getMessage();

        if (message.equalsIgnoreCase("/help")) {
            event.setCancelled(true);
            sendHelpMessage(player);
            return;
        }

        if (!message.startsWith("/") && !player.hasPermission("bs.ban")) {
            String playerName = player.getName();
            if (spammingPlayers.contains(playerName)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        Data.PREFIX + "&cBitte warte einen Moment... Verdacht auf Spam!"));
            } else {
                spammingPlayers.add(playerName);
                ProxyServer.getInstance().getScheduler().schedule(Main.instance,
                        () -> spammingPlayers.remove(playerName),
                        3, TimeUnit.SECONDS);
            }
        }

        if (MuteManager.isMuted(player.getName())) {
            long currentTime = System.currentTimeMillis();
            long muteEndTime = MuteManager.getEnd(player.getName());

            if (muteEndTime < currentTime && muteEndTime != -1L) {
                MuteManager.unMute(player.getName(), "Automatische Cloud");
            } else if (!message.startsWith("/")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        Data.PREFIX + "&7Derzeit ist der Chat für dich deaktiviert!\n" +
                                Data.PREFIX + "&7Grund: &c" + MuteManager.getReason(player.getName()) + "\n" +
                                Data.PREFIX + "&7Verbleibende Zeit: &c" + MuteManager.getRemainingTime(player.getName())));
            }
        }
    }

    private void sendHelpMessage(ProxiedPlayer player) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8● &9INFORMATIONEN ZUM SERVER &8●"));
        player.sendMessage("");
        player.sendMessage("  " + ChatColor.BLUE + "/lobby " + ChatColor.GRAY + "- " + ChatColor.WHITE + "Kehre in die Lobby zurück!");
        player.sendMessage("  " + ChatColor.BLUE + "/report " + ChatColor.GRAY + "- " + ChatColor.WHITE + "Reporte einen verdächtigen Spieler!");
        player.sendMessage("  " + ChatColor.BLUE + "/msg " + ChatColor.GRAY + "- " + ChatColor.WHITE + "Sende einem Spieler eine private Nachricht!");
        player.sendMessage("  " + ChatColor.BLUE + "/party " + ChatColor.GRAY + "- " + ChatColor.WHITE + "Erstelle eine Party!");
        player.sendMessage("  " + ChatColor.BLUE + "/premium /yt " + ChatColor.GRAY + "- " + ChatColor.WHITE + "Erhalte Infos zu den Rängen!");
        player.sendMessage("");
    }
}
