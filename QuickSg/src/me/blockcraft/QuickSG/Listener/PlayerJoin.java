package me.blockcraft.QuickSG.Listener;

import me.blockcraft.QuickSG.ItemAPI;
import me.blockcraft.QuickSG.LocationAPI;
import me.blockcraft.QuickSG.Main;
import me.blockcraft.QuickSG.tools.Countdowns;
import me.blockcraft.QuickSG.tools.Schutzzeit;
import me.blockcraft.QuickSG.tools.Scoreboards.Scoreboard_InGame;
import me.blockcraft.QuickSG.tools.Scoreboards.Scoreboard_Lobby;
import me.blockcraft.QuickSG.tools.Spectator;
import me.blockcraft.QuickSG.tools.items.KitSelector;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitScheduler;

public class PlayerJoin
        implements Listener {
    public static int i = 31;
    public static int schedulerjoin;
    public boolean started;

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    @EventHandler
    public void on(PlayerJoinEvent e) {
        for (World w : Bukkit.getWorlds()) {
            w.setThundering(false);
            w.setStorm(false);
            w.setTime(1000);
        }
        e.setJoinMessage("");
        Player p = e.getPlayer();
        if (Main.State_Lobby) {
            Scoreboard_Lobby.add(p);
            Main.online.add(p);
            p.teleport(LocationAPI.getLocation("Lobby"));
            KitSelector.Kit1.add(p);
            for (Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_Lobby.add(all);
            }
            for (PotionEffect effect : p.getActivePotionEffects()) {
                p.removePotionEffect(effect.getType());
            }
            p.setExp(0.0f);
            p.setLevel(0);
            p.setMaxHealth(20.0);
            p.setHealth(20.0);
            p.setGameMode(GameMode.ADVENTURE);
            p.setFoodLevel(20);
            p.getInventory().clear();
            p.getInventory().setHelmet(new ItemStack(Material.AIR));
            p.getInventory().setChestplate(new ItemStack(Material.AIR));
            p.getInventory().setLeggings(new ItemStack(Material.AIR));
            p.getInventory().setBoots(new ItemStack(Material.AIR));
            ItemStack kit = new ItemStack(Material.CHEST);
            ItemMeta kits = kit.getItemMeta();
            kits.setDisplayName("\u00a7» \u00a79Kits");
            kit.setItemMeta(kits);
            ItemStack hub = new ItemStack(Material.SLIME_BALL);
            ItemMeta hubm = hub.getItemMeta();
            hubm.setDisplayName("\u00a7» \u00a79Hub");
            hub.setItemMeta(hubm);
            if (p.hasPermission("bs.startGame")) {
                ItemStack start = new ItemStack(Material.DIAMOND);
                ItemMeta startm = start.getItemMeta();
                startm.setDisplayName("\u00a7» \u00a79Spiel Starten");
                start.setItemMeta(startm);
                p.getInventory().setItem(3, start);
                p.getInventory().setItem(8, hub);
                p.getInventory().setItem(0, ItemAPI.Stack("\u00a7» \u00a79Stats", 397, "", 1, (short) 3));
                p.getInventory().setItem(5, kit);
            } else {
                p.getInventory().setItem(8, hub);
                p.getInventory().setItem(0, ItemAPI.Stack("\u00a7» \u00a79Stats", 397, "", 1, (short) 3));
                p.getInventory().setItem(4, kit);
            }
            Bukkit.broadcastMessage(Main.Prefix + "\u00a7f" + p.getName() + " \u00a77hat das Spiel betreten!");
            final int minPlayers = Main.getInstance().getConfig().getInt("MinPlayers");
            if (Bukkit.getOnlinePlayers().size() == minPlayers || Bukkit.getOnlinePlayers().size() > minPlayers) {
                if (this.started) return;
                this.started = true;
                schedulerjoin = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
                    --PlayerJoin.i;
                    if (Bukkit.getOnlinePlayers().size() < minPlayers) {
                        for (Player all : Bukkit.getOnlinePlayers()) {
                            all.setLevel(0);
                        }
                        PlayerJoin.i = 30;
                    }
                    for (Player all : Main.online) {
                        all.setLevel(PlayerJoin.i);
                    }
                    if (PlayerJoin.i == 15) {
                        Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde startet in \u00a7r15 \u00a77Sekunden.");
                    }
                    if (PlayerJoin.i == 10) {
                        Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde startet in \u00a7r10 \u00a77Sekunden.");
                    }
                    if (PlayerJoin.i == 5) {
                        Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde startet in \u00a7r5 \u00a77Sekunden.");
                    }
                    if (PlayerJoin.i == 4) {
                        Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde startet in \u00a7r4 \u00a77Sekunden.");
                    }
                    if (PlayerJoin.i == 3) {
                        Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde startet in \u00a7r3 \u00a77Sekunden.");
                    }
                    if (PlayerJoin.i == 2) {
                        Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde startet in \u00a7r2 \u00a77Sekunden.");
                    }
                    if (PlayerJoin.i == 1) {
                        Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde startet in \u00a7r1 \u00a77Sekunden.");
                    }
                    if (PlayerJoin.i == 0) {
                        Bukkit.getScheduler().cancelTask(PlayerJoin.schedulerjoin);
                        Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde wurde gestartet.");
                        Main.State_InGame = true;
                        Main.State_Lobby = false;
                        Main.Schutzzeit = true;
                        int i = 0;
                        for (Player all22 : Bukkit.getOnlinePlayers()) {
                            all22.getInventory().clear();
                            all22.teleport(Main.getSpawnPos(++i));
                            if (i == 8) break;
                        }
                        for (Player all22 : Bukkit.getOnlinePlayers()) {
                            all22.getInventory().clear();
                            all22.setLevel(0);
                            all22.setExp(0.0f);
                            all22.setLevel(0);
                            all22.setGameMode(GameMode.SURVIVAL);
                            all22.playSound(all22.getLocation(), Sound.IRONGOLEM_DEATH, 4.0f, 4.0f);
                            all22.playEffect(all22.getLocation(), Effect.LARGE_SMOKE, 2);
                            all22.setMaxHealth(14.0);
                            Scoreboard_InGame.add(all22);
                        }
                        for (Player Kit2222222 : KitSelector.Kit1) {
                            Kit2222222.getInventory().addItem(new ItemStack(Material.WOOD_SWORD));
                        }
                        for (Player Kit2222222 : KitSelector.Kit2) {
                            Kit2222222.getInventory().addItem(new ItemStack(Material.BOW));
                            Kit2222222.getInventory().addItem(new ItemStack(Material.ARROW, 12));
                        }
                        for (Player Kit2222222 : KitSelector.Kit3) {
                            Kit2222222.setMaxHealth(20.0);
                            Kit2222222.setHealth(20.0);
                        }
                        for (Player Kit2222222 : KitSelector.Kit4) {
                            Kit2222222.getInventory().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
                            Kit2222222.getInventory().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
                            Kit2222222.getInventory().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
                        }
                        for (Player Kit2222222 : KitSelector.Kit5) {
                            Kit2222222.getInventory().addItem(new ItemStack(Material.WOOD_SWORD));
                            ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
                            ItemMeta meta = item.getItemMeta();
                            meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
                            item.setItemMeta(meta);
                            Kit2222222.getInventory().setChestplate(item);
                        }

                        Main.countdown = 5;
                        Main.countdownRunning = true;
                        this.addCooldown();
                    }
                }, 20, 20);
            } else {
                if (Bukkit.getOnlinePlayers().size() >= minPlayers) return;
                Bukkit.getScheduler().cancelTask(schedulerjoin);
                for (Player all2 : Bukkit.getOnlinePlayers()) {
                    all2.setLevel(30);
                }
                i = 30;
                this.started = false;
            }
        } else {
            if (!Main.State_InGame) return;
            for (Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_InGame.add(all);
            }
        }
    }

    @SuppressWarnings("unused")
    @EventHandler
    public void on(PlayerLoginEvent e) {
        Player p = e.getPlayer();
        if (!Main.State_Lobby && Bukkit.getOnlinePlayers().size() > 4) {
            e.allow();
        }
    }

    @EventHandler
    public void on2(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!Main.State_Lobby) {
            Spectator.add(p);
        }
    }

    private void addCooldown() {
        Boolean cooldowns = true;
        final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleAsyncRepeatingTask(Main.getInstance(), () -> {
            --Main.countdown;
            if (Main.countdown == 5) {
                for (final Player all : Main.online) {
                    all.sendTitle("\u00a795", "");
                }
            }
            if (Main.countdown == 4) {
                for (final Player all : Main.online) {
                    all.sendTitle("\u00a794", "");
                }
            }
            if (Main.countdown == 3) {
                for (final Player all : Main.online) {
                    all.sendTitle("\u00a793", "");
                }
            }
            if (Main.countdown == 2) {
                for (final Player all : Main.online) {
                    all.sendTitle("\u00a792", "");
                }
            }
            if (Main.countdown == 1) {
                for (final Player all : Main.online) {
                    all.sendTitle("\u00a791", "");
                }
            }
            if (Main.countdown == 0) {
                Main.countdownRunning = false;
                Schutzzeit.start();
                Countdowns.start();
            }
        }, 20L, 20L);
    }

}

