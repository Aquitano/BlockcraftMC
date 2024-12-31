package me.blockcraft.rush.Listener;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.RushManager;
import me.blockcraft.rush.Scoreboards.Scoreboard_DM;
import me.blockcraft.rush.Scoreboards.Scoreboard_Lobby;
import me.blockcraft.rush.Scoreboards.Scoreboard_Shopping;
import me.blockcraft.rush.Scoreboards.Scoreboard_SuchZeit;
import me.blockcraft.rush.tools.Spectator;
import me.blockcraft.rush.tools.Timer_SuchZeit;
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
import org.bukkit.potion.PotionEffectType;

public class PlayerJoin implements Listener {
    public static int i;
    public static int schedulerjoin;

    static {
        PlayerJoin.i = 31;
        PlayerJoin.schedulerjoin = 0;
    }

    public boolean started;

    public PlayerJoin() {
        this.started = false;
    }

    @EventHandler
    public void on(final PlayerJoinEvent e) {
        for (final World w : Bukkit.getWorlds()) {
            w.setThundering(false);
            w.setStorm(false);
            w.setTime(1000L);
        }
        e.setJoinMessage("");
        final Player p = e.getPlayer();
        if (Main.State_Lobby) {
            Scoreboard_Lobby.add(p);
            Main.online.add(p);
            p.teleport(RushManager.getLocation("Lobby"));
            for (final Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_Lobby.add(all);
            }
            for (final PotionEffect effect : p.getActivePotionEffects()) {
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
            p.sendMessage("");
            p.sendMessage("\u00a77Sammle bei \u00a7bEmeraldRush \u00a77mäglichst viele Emeralds!");
            p.sendMessage("\u00a77Pro Emerald, den du sammelst, erhältst du 10 Punkte.");
            p.sendMessage("\u00a77Nutze sie, um mit dem Villager zu handeln und rüste");
            p.sendMessage("\u00a77dich für das große Deathmatch gut aus!");
            p.sendMessage("");
            final ItemStack kit = new ItemStack(Material.CHEST);
            final ItemMeta kits = kit.getItemMeta();
            kits.setDisplayName("\u00a78» \u00a79Perks");
            kit.setItemMeta(kits);
            final ItemStack hub = new ItemStack(Material.SLIME_BALL);
            final ItemMeta hubm = hub.getItemMeta();
            hubm.setDisplayName("\u00a78» \u00a79Hub");
            hub.setItemMeta(hubm);
            if (p.hasPermission("bs.startGame")) {
                final ItemStack start = new ItemStack(Material.DIAMOND);
                final ItemMeta startm = start.getItemMeta();
                startm.setDisplayName("\u00a78» \u00a79Spiel Starten");
                start.setItemMeta(startm);
                p.getInventory().setItem(1, start);
                p.getInventory().setItem(8, hub);
                p.getInventory().setItem(4, RushManager.Stack("\u00a78» \u00a79Stats", 397, "", 1, (short) 3));
                p.getInventory().setItem(0, kit);
            } else {
                p.getInventory().setItem(8, hub);
                p.getInventory().setItem(4, RushManager.Stack("\u00a78» \u00a79Stats", 397, "", 1, (short) 3));
                p.getInventory().setItem(0, kit);
            }
            Bukkit.broadcastMessage(Main.Prefix + "\u00a7f" + p.getName() + " \u00a77hat das Spiel betreten!");
            final int minPlayers = Main.getInstance().getConfig().getInt("MinPlayers");
            if (Bukkit.getOnlinePlayers().size() == minPlayers || Bukkit.getOnlinePlayers().size() > minPlayers) {
                if (!this.started) {
                    this.started = true;
                    PlayerJoin.schedulerjoin = Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(Main.getInstance(), () -> {
                        --PlayerJoin.i;
                        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
                            for (final Player all : Bukkit.getOnlinePlayers()) {
                                all.setLevel(0);
                            }
                            PlayerJoin.i = 30;
                        }
                        for (final Player all : Main.online) {
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
                            for (final Player all : Bukkit.getOnlinePlayers()) {
                                all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                            }
                        }
                        if (PlayerJoin.i == 4) {
                            Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde startet in \u00a7r4 \u00a77Sekunden.");
                            for (final Player all : Bukkit.getOnlinePlayers()) {
                                all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                            }
                        }
                        if (PlayerJoin.i == 3) {
                            Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde startet in \u00a7r3 \u00a77Sekunden.");
                            for (final Player all : Bukkit.getOnlinePlayers()) {
                                all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                            }
                        }
                        if (PlayerJoin.i == 2) {
                            Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde startet in \u00a7r2 \u00a77Sekunden.");
                            for (final Player all : Bukkit.getOnlinePlayers()) {
                                all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                            }
                        }
                        if (PlayerJoin.i == 1) {
                            Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde startet in \u00a7r1 \u00a77Sekunden.");
                            for (final Player all : Bukkit.getOnlinePlayers()) {
                                all.playSound(all.getLocation(), Sound.CLICK, 4.0f, 4.0f);
                            }
                        }
                        if (PlayerJoin.i == 0) {
                            Bukkit.getScheduler().cancelTask(PlayerJoin.schedulerjoin);
                            Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Runde wurde gestartet.");
                            Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Such Zeit hat begonnen. Baue so viele Emerald-Ore ab wie es nur geht");
                            Bukkit.broadcastMessage(Main.Prefix + "\u00a77Die Suchzeit endet in \u00a7r3\u00a77 Minuten!");
                            Main.State_InGame = true;
                            Main.State_Lobby = false;
                            Main.State_SuchZeit = true;
                            Timer_SuchZeit.start();
                            for (final Player all2 : Bukkit.getOnlinePlayers()) {
                                all2.getInventory().clear();
                                all2.setLevel(0);
                                all2.setExp(0.0f);
                                all2.setLevel(0);
                                all2.setGameMode(GameMode.SURVIVAL);
                                all2.playSound(all2.getLocation(), Sound.IRONGOLEM_DEATH, 4.0f, 4.0f);
                                all2.playEffect(all2.getLocation(), Effect.LARGE_SMOKE, 2);
                                Scoreboard_SuchZeit.add(all2);
                                final ItemStack di = new ItemStack(Material.IRON_PICKAXE);
                                final ItemMeta dim = di.getItemMeta();
                                dim.setDisplayName("\u00a77Spitzhacke");
                                dim.addEnchant(Enchantment.DURABILITY, 10, true);
                                dim.addEnchant(Enchantment.DIG_SPEED, 5, true);
                                di.setItemMeta(dim);
                                all2.getInventory().addItem(di);
                                all2.teleport(RushManager.getLocation("Such"));
                            }
                            for (final Player all2 : Bukkit.getOnlinePlayers()) {
                                all2.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1));
                            }
                        }
                    }, 20L, 20L);
                }
            } else if (Bukkit.getOnlinePlayers().size() < minPlayers) {
                Bukkit.getScheduler().cancelTask(PlayerJoin.schedulerjoin);
                for (final Player all2 : Bukkit.getOnlinePlayers()) {
                    all2.setLevel(30);
                }
                PlayerJoin.i = 30;
                this.started = false;
            }
        } else if (Main.State_InGame) {
            for (final Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_SuchZeit.add(all);
            }
        } else if (Main.State_DM) {
            for (final Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_DM.add(all);
            }
        } else if (Main.State_Shopping) {
            for (final Player all : Bukkit.getOnlinePlayers()) {
                Scoreboard_Shopping.add(all);
            }
        }
    }

    @SuppressWarnings("unused")
    @EventHandler
    public void on(final PlayerLoginEvent e) {
        final Player p = e.getPlayer();
        if (!Main.State_Lobby && Bukkit.getOnlinePlayers().size() > 4) {
            e.allow();
        }
    }

    @EventHandler
    public void on2(final PlayerJoinEvent e) {
        final Player p = e.getPlayer();
        if (!Main.State_Lobby) {
            Spectator.add(p);
            p.teleport(RushManager.getLocation("Spectator"));
        }
    }
}
