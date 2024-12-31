package me.blockcraft.lobby.secrets;

import me.blockcraft.coins.CAPI;
import me.blockcraft.lobby.APIs.TitlesAPI;
import me.blockcraft.lobby.Lobby;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;

public class Secrets implements Listener {
    public static File Data;
    public static FileConfiguration File;
    public static Lobby main;

    static {
        Secrets.Data = new File("plugins/Lobby", "cache.yml");
        Secrets.File = YamlConfiguration.loadConfiguration(Secrets.Data);
    }

    private final HashSet<String> secrets = new HashSet<>();

    public Secrets(final Lobby main) {
        Secrets.main = main;
    }

    @EventHandler
    public void SignChange(final SignChangeEvent e) {
        final Player p = e.getPlayer();
        if (p.hasPermission("secrets.use") && e.getLine(0).equals("[S]")) {
            if (e.getLine(1).equalsIgnoreCase("Hinrichtung")) {
                e.setLine(0, "\u00a78\u00a7m-----------");
                e.setLine(1, "\u00a77[SECRET]");
                e.setLine(2, "\u00a79Hinrichtung");
                e.setLine(3, "\u00a78\u00a7m-----------");
                secrets.add("Hinrichtung");
            } else if (e.getLine(1).equalsIgnoreCase("Abfall")) {
                e.setLine(0, "\u00a78\u00a7m-----------");
                e.setLine(1, "\u00a77[SECRET]");
                e.setLine(2, "\u00a79Abfall");
                e.setLine(3, "\u00a78\u00a7m-----------");
                secrets.add("Abfall");
            } else if (e.getLine(1).equalsIgnoreCase("Wagen")) {
                e.setLine(0, "\u00a78\u00a7m-----------");
                e.setLine(1, "\u00a77[SECRET]");
                e.setLine(2, "\u00a79Auf dem Wagen");
                e.setLine(3, "\u00a78\u00a7m-----------");
                secrets.add("Wagen");
            } else if (e.getLine(1).equalsIgnoreCase("Statue")) {
                e.setLine(0, "\u00a78\u00a7m-----------");
                e.setLine(1, "\u00a77[SECRET]");
                e.setLine(2, "\u00a79Statue");
                e.setLine(3, "\u00a78\u00a7m-----------");
                secrets.add("Statue");
            } else if (e.getLine(1).equalsIgnoreCase("Abgelegen")) {
                e.setLine(0, "\u00a78\u00a7m-----------");
                e.setLine(1, "\u00a77[SECRET]");
                e.setLine(2, "\u00a79Abgelegen");
                e.setLine(3, "\u00a78\u00a7m-----------");
                secrets.add("Abgelegen");
            } else if (e.getLine(1).equalsIgnoreCase("Zimmer")) {
                e.setLine(0, "\u00a78\u00a7m-----------");
                e.setLine(1, "\u00a77[SECRET]");
                e.setLine(2, "\u00a79Das Zimmer");
                e.setLine(3, "\u00a78\u00a7m-----------");
                secrets.add("Zimmer");
            } else if (e.getLine(1).equalsIgnoreCase("Abwasser")) {
                e.setLine(0, "\u00a78\u00a7m-----------");
                e.setLine(1, "\u00a77[SECRET]");
                e.setLine(2, "\u00a79Abwasser?");
                e.setLine(3, "\u00a78\u00a7m-----------");
                secrets.add("Abwasser");
            } else if (e.getLine(1).equalsIgnoreCase("Ruhe")) {
                e.setLine(0, "\u00a78\u00a7m-----------");
                e.setLine(1, "\u00a77[SECRET]");
                e.setLine(2, "\u00a79Ruh dich aus!");
                e.setLine(3, "\u00a78\u00a7m-----------");
                secrets.add("Ruhe");
            } else if (e.getLine(1).equalsIgnoreCase("Nacht")) {
                e.setLine(0, "\u00a78\u00a7m-----------");
                e.setLine(1, "\u00a77[SECRET]");
                e.setLine(2, "\u00a79Gute Nacht!");
                e.setLine(3, "\u00a78\u00a7m-----------");
                secrets.add("Nacht");
            }
        }
    }

    @EventHandler
    public void onFound(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getState() instanceof Sign) {
            final Sign s = (Sign) e.getClickedBlock().getState();

            HashMap<String, String> secretsMap = new HashMap<String, String>() {{
                put("Hinrichtung", "\u00a79Hinrichtung");
                put("Abfall", "\u00a79Abfall");
                put("Wagen", "\u00a79Auf dem Wagen");
                put("Statue", "\u00a79Statue");
                put("Abgelegen", "\u00a79Abgelegen");
                put("Zimmer", "\u00a79Das Zimmer");
                put("Abwasser", "\u00a79Abwasser?");
                put("Ruhe", "\u00a79Ruh dich aus!");
                put("Nacht", "\u00a79Gute Nacht!");
            }};

            for (HashMap.Entry<String, String> entry : secretsMap.entrySet()) {
                String secretKey = entry.getKey();
                String displayName = entry.getValue();

                if (s.getLine(2).equalsIgnoreCase(displayName)) {
                    String playerKey = p.getName() + "." + secretKey;

                    if (Secrets.File.contains(playerKey)) {
                        p.sendMessage("\u00a78» \u00a7cDieses Secret hast du bereits gefunden!");
                    } else {
                        TitlesAPI.sendTitle(p, 10, 20, 10, "\u00a77Secret gefunden!", "\u00a7b+50 Coins");
                        Secrets.File.set(playerKey, true);

                        try {
                            Secrets.File.save(Secrets.Data);
                        } catch (IOException e2) {
                            e2.printStackTrace();
                        }

                        p.sendMessage(String.format(
                                "\u00a79Secrets \u00a78● \u00a77Du hast folgendes \u00a7fSecrets \u00a77gefunden\u00a78: \u00a7f%s\u00a78!",
                                secretKey
                        ));
                        p.sendMessage("\u00a79Secrets \u00a78● \u00a77Herzlichen Glückwunsch!");
                        p.sendMessage("\u00a7b+50 Coins");
                        p.playSound(p.getLocation(), org.bukkit.Sound.ENDERDRAGON_GROWL, 1.0f, 1.0f);

                        CAPI.addCoins(p.getUniqueId().toString(), 50);
                        p.setLevel(CAPI.getCoins(p.getUniqueId().toString()));
                    }
                    break;
                }
            }
        }
    }
}
