package me.blockcraft.duell;

import me.blockcraft.duell.listeners.*;
import me.blockcraft.duell.listeners.sg.ChestOpenerSG;
import me.blockcraft.duell.listeners.sg.DeathListenerSG;
import me.blockcraft.duell.listeners.sg.RoundStartListenerSG;
import me.blockcraft.duell.listeners.superjump.RoundStartListenerSJ;
import me.blockcraft.duell.listeners.superjump.SuperJumpMoveListener;
import me.blockcraft.duell.listeners.vs.DeathListenerVS;
import me.blockcraft.duell.listeners.vs.RoundStartListener;
import me.blockcraft.duell.methods.MySQL;
import me.blockcraft.duell.methods.PlayerManager;
import me.blockcraft.duell.methods.StackManager;
import me.blockcraft.duell.methods.StatsAPI;
import me.eventseen.Data.Data;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Duell extends JavaPlugin implements Listener {
    public static final String PREFIX = ChatColor.BLUE + "Duell " + ChatColor.DARK_GRAY + "» " + ChatColor.RESET;
    public static String currentMode;
    public static int round;
    public static int runde;
    public static int i;
    public static File Cache;
    public static FileConfiguration fileData;
    public static ArrayList<Player> online;
    public static HashMap<Location, Player> spawnLocation;
    public static int mysql;
    public static int cooldown;
    public static Duell Instance;

    static {
        Duell.currentMode = null;
        Duell.round = 1;
        Duell.runde = 600;
        Duell.i = 10;
        Duell.Cache = new File("plugins/Duell", "Cache.yml");
        Duell.fileData = YamlConfiguration.loadConfiguration(Duell.Cache);
        Duell.online = new ArrayList<>();
        Duell.spawnLocation = new HashMap<>();
        Duell.mysql = 5000;
        Duell.cooldown = 5;
        Duell.Instance = null;
    }

    public boolean voted;
    public boolean vs;
    public boolean SG;
    public boolean SJ;
    public boolean noPvP;
    public boolean InGame;
    public boolean CountdownRunning;
    public boolean Lobby;
    public boolean full;
    public boolean motd;
    public String SJ1m;
    public String SJ2m;
    public String SJ3m;
    public String SJ4m;
    public String SJ5m;
    public String vs1m;
    public String vs2m;
    public String vs3m;
    public String vs4m;
    public String sg1m;
    public String sg2m;
    public String s3gm;
    public boolean cooldowns;
    public List<Location> openedchests;
    public HashMap<Location, Inventory> sgchest;

    public Duell() {
        this.voted = false;
        this.vs = false;
        this.SG = false;
        this.SJ = false;
        this.noPvP = false;
        this.InGame = false;
        this.CountdownRunning = false;
        this.Lobby = false;
        this.full = false;
        this.motd = false;
        this.SJ1m = this.getConfig().getString("SJMap.1");
        this.SJ2m = this.getConfig().getString("SJMap.2");
        this.SJ3m = this.getConfig().getString("SJMap.3");
        this.SJ4m = this.getConfig().getString("SJMap.4");
        this.SJ5m = this.getConfig().getString("SJMap.5");
        this.vs1m = this.getConfig().getString("1vs1Map.1");
        this.vs2m = this.getConfig().getString("1vs1Map.2");
        this.vs3m = this.getConfig().getString("1vs1Map.3");
        this.vs4m = this.getConfig().getString("1vs1Map.4");
        this.sg1m = this.getConfig().getString("SGMap.1");
        this.sg2m = this.getConfig().getString("SGMap.2");
        this.s3gm = this.getConfig().getString("SGMap.3");
        this.cooldowns = false;
        this.openedchests = new ArrayList<>();
        this.sgchest = new HashMap<>();
        Duell.Instance = this;
    }

    public static Duell getInstance() {
        return Duell.Instance;
    }

    public static void connect(final Player T, final String Servername) {
        final ByteArrayOutputStream Out = new ByteArrayOutputStream();
        final DataOutputStream out = new DataOutputStream(Out);
        try {
            out.writeUTF("Connect");
            out.writeUTF(Servername);
        } catch (Exception ex) {
        }
        T.sendPluginMessage(getInstance(), "BungeeCord", Out.toByteArray());
    }

    public void onEnable() {
        System.out.println("[DUELL] Plugin gestartet.");
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        this.getServer().getPluginManager().registerEvents(new ChestClickListener(this), this);
        this.getServer().getPluginManager().registerEvents(new PlayerManager(this), this);
        this.getServer().getPluginManager().registerEvents(new ChestInvClickListener(this), this);
        this.getServer().getPluginManager().registerEvents(new RoundStartListener(this), this);
        this.getServer().getPluginManager().registerEvents(new DeathListenerVS(this), this);
        this.getServer().getPluginManager().registerEvents(new RoundStartListenerSG(this), this);
        this.getServer().getPluginManager().registerEvents(new DeathListenerSG(this), this);
        this.getServer().getPluginManager().registerEvents(new ChestOpenerSG(this), this);
        this.getServer().getPluginManager().registerEvents(new PreventListeners(), this);
        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getPluginManager().registerEvents(new DisablePvPListener(this), this);
        this.getServer().getPluginManager().registerEvents(new SuperJumpMoveListener(this), this);
        this.getServer().getPluginManager().registerEvents(new RoundStartListenerSJ(this), this);
        this.getServer().getPluginManager().registerEvents(new PlayerLeftListener(this), this);
        this.getServer().getPluginManager().registerEvents(new MainJoinListener(this), this);
        this.getServer().getPluginManager().registerEvents(new StatsMenuOpener(this), this);
        MySQL.setStandardMySQL();
        MySQL.readMySQL();
        MySQL.connect();
        MySQL.createTable();
        this.loadConfig();
        this.saveConfig();
        this.voted = false;
        this.vs = false;
        this.SG = false;
        this.Lobby = true;
        this.noPvP = true;
        this.full = false;
        this.motd = true;
        for (final Entity entity : RoundStartListener.getpos(1, 1).getWorld().getEntities()) {
            if (entity.getType() != EntityType.PLAYER && entity.getType() != EntityType.ITEM_FRAME
                    && entity.getType() != EntityType.MINECART) {
                entity.remove();
            }
        }
        final World world = Bukkit.getWorld(RoundStartListener.getpos(1, 1).getWorld().getName());
        final List<Entity> entList = world.getEntities();
        for (final Entity current : entList) {
            if (current instanceof Item) {
                current.remove();
            }
        }
    }

    public void onDisable() {
        MySQL.close();
        System.out.println("[DUELL] Plugin gestoppt.");
        Duell.Cache.delete();
    }

    public void loadConfig() {
        final FileConfiguration cfg = this.getConfig();
        cfg.options().copyDefaults(true);
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String commandLabel,
                             final String[] args) {
        final Player p = (Player) sender;
        if (cmd.getName().equalsIgnoreCase("setspawn") && args.length == 1) {
            if (args[0].equalsIgnoreCase("spawn")) {
                this.getConfig().set("spawn.world", p.getLocation().getWorld().getName());
                this.getConfig().set("spawn.x", p.getLocation().getX());
                this.getConfig().set("spawn.y", p.getLocation().getY());
                this.getConfig().set("spawn.z", p.getLocation().getZ());
                this.getConfig().set("spawn.yaw", p.getLocation().getYaw());
                this.getConfig().set("spawn.pitch", p.getLocation().getPitch());
                p.sendMessage(PREFIX + "\u00a77Spawn gesetzt.");
                this.saveConfig();
            } else if (args[0].equalsIgnoreCase("1vs11p1")) {
                this.setLocation(1, 1, p);
            } else if (args[0].equalsIgnoreCase("1vs11p2")) {
                this.setLocation(1, 2, p);
            } else if (args[0].equalsIgnoreCase("1vs12p1")) {
                this.setLocation(2, 1, p);
            } else if (args[0].equalsIgnoreCase("1vs12p2")) {
                this.setLocation(2, 2, p);
            } else if (args[0].equalsIgnoreCase("1vs13p1")) {
                this.setLocation(3, 1, p);
            } else if (args[0].equalsIgnoreCase("1vs13p2")) {
                this.setLocation(3, 2, p);
            } else if (args[0].equalsIgnoreCase("1vs14p1")) {
                this.setLocation(4, 1, p);
            } else if (args[0].equalsIgnoreCase("1vs14p2")) {
                this.setLocation(4, 2, p);
            } else if (args[0].equalsIgnoreCase("SG1p1")) {
                this.setLocationSG(1, 1, p);
            } else if (args[0].equalsIgnoreCase("SG1p2")) {
                this.setLocationSG(1, 2, p);
            } else if (args[0].equalsIgnoreCase("SG2p1")) {
                this.setLocationSG(2, 1, p);
            } else if (args[0].equalsIgnoreCase("SG2p2")) {
                this.setLocationSG(2, 2, p);
            } else if (args[0].equalsIgnoreCase("SG3p1")) {
                this.setLocationSG(3, 1, p);
            } else if (args[0].equalsIgnoreCase("SG3p2")) {
                this.setLocationSG(3, 2, p);
            } else if (args[0].equalsIgnoreCase("SJ1p1")) {
                this.setLocationSJ(1, 1, p);
            } else if (args[0].equalsIgnoreCase("SJ1p2")) {
                this.setLocationSJ(1, 2, p);
            } else if (args[0].equalsIgnoreCase("SJ2p1")) {
                this.setLocationSJ(2, 1, p);
            } else if (args[0].equalsIgnoreCase("SJ2p2")) {
                this.setLocationSJ(2, 2, p);
            } else if (args[0].equalsIgnoreCase("SJ3p1")) {
                this.setLocationSJ(3, 1, p);
            } else if (args[0].equalsIgnoreCase("SJ3p2")) {
                this.setLocationSJ(3, 2, p);
            } else if (args[0].equalsIgnoreCase("SJ4p1")) {
                this.setLocationSJ(4, 1, p);
            } else if (args[0].equalsIgnoreCase("SJ4p2")) {
                this.setLocationSJ(4, 2, p);
            } else if (args[0].equalsIgnoreCase("SJ5p1")) {
                this.setLocationSJ(5, 1, p);
            } else if (args[0].equalsIgnoreCase("SJ5p2")) {
                this.setLocationSJ(5, 2, p);
            }
        }
        return false;
    }

    public Location getSpawn() {
        Location loc;
        final String world = this.getConfig().getString("spawn.world");
        final double x = this.getConfig().getDouble("spawn.x");
        final double y = this.getConfig().getDouble("spawn.y");
        final double z = this.getConfig().getDouble("spawn.z");
        final float yaw = (float) this.getConfig().getDouble("spawn.yaw");
        final float pitch = (float) this.getConfig().getDouble("spawn.pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public Location get1vs11p1() {
        Location loc;
        final String world = this.getConfig().getString("1vs11p1.world");
        final double x = this.getConfig().getDouble("1vs11p1.x");
        final double y = this.getConfig().getDouble("1vs11p1.y");
        final double z = this.getConfig().getDouble("1vs11p1.z");
        final float yaw = (float) this.getConfig().getDouble("1vs11p1.yaw");
        final float pitch = (float) this.getConfig().getDouble("1vs11p1.pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public void setLocation(final int map, final int player, final Player p) {
        this.getConfig().set("1vs1" + map + "p" + player + ".world", p.getLocation().getWorld().getName());
        this.getConfig().set("1vs1" + map + "p" + player + ".x", p.getLocation().getX());
        this.getConfig().set("1vs1" + map + "p" + player + ".y", p.getLocation().getY());
        this.getConfig().set("1vs1" + map + "p" + player + ".z", p.getLocation().getZ());
        this.getConfig().set("1vs1" + map + "p" + player + ".yaw", p.getLocation().getYaw());
        this.getConfig().set("1vs1" + map + "p" + player + ".pitch", p.getLocation().getPitch());
        this.saveConfig();
        p.sendMessage(PREFIX + "\u00a771vs1 Spawn, Map \u00a76" + map + "\u00a77 Spieler\u00a76 " + player
                + " \u00a77gesetzt.");
    }

    public void setLocationSG(final int map, final int player, final Player p) {
        this.getConfig().set("SG" + map + "p" + player + ".world", p.getLocation().getWorld().getName());
        this.getConfig().set("SG" + map + "p" + player + ".x", p.getLocation().getX());
        this.getConfig().set("SG" + map + "p" + player + ".y", p.getLocation().getY());
        this.getConfig().set("SG" + map + "p" + player + ".z", p.getLocation().getZ());
        this.getConfig().set("SG" + map + "p" + player + ".yaw", p.getLocation().getYaw());
        this.getConfig().set("SG" + map + "p" + player + ".pitch", p.getLocation().getPitch());
        this.saveConfig();
        p.sendMessage(PREFIX + "\u00a77SG Spawn, Map \u00a76" + map + "\u00a77 Spieler\u00a76 " + player
                + " \u00a77gesetzt.");
    }

    public void setLocationSJ(final int map, final int player, final Player p) {
        this.getConfig().set("SJ" + map + "p" + player + ".world", p.getLocation().getWorld().getName());
        this.getConfig().set("SJ" + map + "p" + player + ".x", p.getLocation().getX());
        this.getConfig().set("SJ" + map + "p" + player + ".y", p.getLocation().getY());
        this.getConfig().set("SJ" + map + "p" + player + ".z", p.getLocation().getZ());
        this.getConfig().set("SJ" + map + "p" + player + ".yaw", p.getLocation().getYaw());
        this.getConfig().set("SJ" + map + "p" + player + ".pitch", p.getLocation().getPitch());
        this.saveConfig();
        p.sendMessage(PREFIX + "\u00a77SJ Spawn, Map \u00a76" + map + "\u00a77 Spieler\u00a76 " + player
                + " \u00a77gesetzt.");
    }

    @SuppressWarnings("deprecation")
    public void addCooldown() {
        this.cooldowns = true;
        final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleAsyncRepeatingTask(this, () -> {
            --Duell.cooldown;
            if (Duell.cooldown == 5) {
                for (final Player all : Duell.online) {
                    all.sendTitle("\u00a795", "");
                }
            }
            if (Duell.cooldown == 4) {
                for (final Player all : Duell.online) {
                    all.sendTitle("\u00a794", "");
                }
            }
            if (Duell.cooldown == 3) {
                for (final Player all : Duell.online) {
                    all.sendTitle("\u00a793", "");
                }
            }
            if (Duell.cooldown == 2) {
                for (final Player all : Duell.online) {
                    all.sendTitle("\u00a792", "");
                }
            }
            if (Duell.cooldown == 1) {
                for (final Player all : Duell.online) {
                    all.sendTitle("\u00a791", "");
                }
            }
            if (Duell.cooldown == 0) {
                Duell.this.cooldowns = false;
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onMove(final PlayerMoveEvent e) {
        if (this.cooldowns) {
            final Location to = e.getFrom();
            to.setPitch(e.getTo().getPitch());
            to.setYaw(e.getTo().getYaw());
            e.setTo(to);
        }
    }

    public void RestartCountdown() {
        if (!this.CountdownRunning) {
            final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
            scheduler.scheduleSyncRepeatingTask(this, () -> {
                --Duell.i;
                for (final Player all : Duell.online) {
                    all.setLevel(Duell.i);
                }
                if (Duell.i == 5) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX
                            + "\u00a77Der Server startet in \u00a7c5 \u00a77Sekunden neu.");
                }
                if (Duell.i == 4) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX
                            + "\u00a77Der Server startet in \u00a7c4 \u00a77Sekunden neu.");
                }
                if (Duell.i == 3) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX
                            + "\u00a77Der Server startet in \u00a7c3 \u00a77Sekunden neu.");
                }
                if (Duell.i == 2) {
                    Bukkit.broadcastMessage(Data.DUELL_PREFIX
                            + "\u00a77Der Server startet in \u00a7c2 \u00a77Sekunden neu.");
                }
                if (Duell.i == 1) {
                    for (final Player all : Bukkit.getOnlinePlayers()) {
                        Duell.connect(all, "lobby");
                    }
                }
                if (Duell.i == 0) {
                    Bukkit.shutdown();
                }
            }, 20L, 20L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void List(final ServerListPingEvent e) {
        if (this.motd) {
            e.setMotd("\u00a7a-------------");
        }
        if (!this.motd) {
            e.setMotd("\u00a76-------------");
        }
    }

    @EventHandler
    public void onFood(final PlayerDropItemEvent e) {
        if (this.Lobby) {
            e.setCancelled(true);
        } else
            e.setCancelled(this.noPvP);
    }

    @SuppressWarnings("unused")
    @EventHandler
    public void onEntityDamage(final EntityDamageEvent e) {
        if (this.Lobby && e.getEntity() instanceof Player) {
            final Player p = (Player) e.getEntity();
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
                e.setCancelled(true);
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
                e.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("unused")
    public void openStatsInv(final Player p) {
        final Inventory inv = Bukkit.createInventory(null, 9, "Stats");
        for (final Player all : Duell.online) {
            final ItemStack SI2 = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
            final ItemStack SI3 = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
            if (Duell.online.size() == 2) {
                final double Kills1 = StatsAPI.getKills(p.getUniqueId().toString(), p.getName());
                final double deaths1 = StatsAPI.getDeaths(p.getUniqueId().toString(), p.getName());
                final double kdr1 = Kills1 / deaths1;
                final double Kdr1 = Math.round(kdr1 * 100.0) / 100.0;
                final List<String> lore1 = new ArrayList<>();
                lore1.add("\u00a78● \u00a79Kills\u00a78: \u00a77"
                        + StatsAPI.getKills(p.getUniqueId().toString(), p.getName()));
                lore1.add("\u00a78● \u00a79Gewonnen\u00a78: \u00a77"
                        + StatsAPI.getWins(p.getUniqueId().toString(), p.getName()));
                lore1.add("\u00a78● \u00a79Tode\u00a78: \u00a77"
                        + StatsAPI.getDeaths(p.getUniqueId().toString(), p.getName()));
                if (deaths1 > 1.0) {
                    lore1.add("\u00a78● \u00a79KD/R\u00a78: \u00a77" + Kdr1);
                } else {
                    lore1.add("\u00a78● \u00a79KD/R\u00a78: \u00a77-");
                }
                final SkullMeta SI1Meta = (SkullMeta) SI3.getItemMeta();
                SI1Meta.setOwner(p.getName());
                SI1Meta.setDisplayName("\u00a7e" + p.getName());
                SI1Meta.setLore(lore1);
                SI3.setItemMeta(SI1Meta);
                final ItemStack Global = StackManager.Skull("\u00a78● \u00a7bGLOBAL WINS \u00a78●", Material.SKULL_ITEM,
                        "\u00a77Klicke zum anzeigen", 1, (short) 3, "Kevos");
                inv.setItem(4, Global);
                for (final Player players : Duell.online) {
                    if (players != p) {
                        final double Kills2 = StatsAPI.getKills(players.getUniqueId().toString(), players.getName());
                        final double deaths2 = StatsAPI.getDeaths(players.getUniqueId().toString(), players.getName());
                        final double kdr2 = Kills2 / deaths2;
                        final double Kdr2 = Math.round(kdr2 * 100.0) / 100.0;
                        final ArrayList<String> SI2Lore = new ArrayList<>();
                        SI2Lore.add("\u00a78● \u00a79Kills\u00a78: \u00a77"
                                + StatsAPI.getKills(players.getUniqueId().toString(), players.getName()));
                        SI2Lore.add("\u00a78● \u00a79Gewonnen\u00a78: \u00a77"
                                + StatsAPI.getWins(players.getUniqueId().toString(), players.getName()));
                        SI2Lore.add("\u00a78● \u00a79Tode\u00a78: \u00a77"
                                + StatsAPI.getDeaths(players.getUniqueId().toString(), players.getName()));
                        if (deaths2 > 1.0) {
                            SI2Lore.add("\u00a78● \u00a79KD/R\u00a78: \u00a77" + Kdr2);
                        } else {
                            SI2Lore.add("\u00a78● \u00a79KD/R\u00a78: \u00a77-");
                        }
                        final SkullMeta SI2Meta = (SkullMeta) SI2.getItemMeta();
                        SI2Meta.setOwner(players.getName());
                        SI2Meta.setDisplayName("\u00a7e" + players.getName());
                        SI2Meta.setLore(SI2Lore);
                        SI2.setItemMeta(SI2Meta);
                    }
                }
                inv.setItem(0, SI3);
                inv.setItem(8, SI2);
                p.openInventory(inv);
            } else {
                final double Kills3 = StatsAPI.getKills(p.getUniqueId().toString(), p.getName());
                final double deaths3 = StatsAPI.getDeaths(p.getUniqueId().toString(), p.getName());
                final double kdr3 = Kills3 / deaths3;
                final double Kdr3 = Math.round(kdr3 * 100.0) / 100.0;
                final ItemStack SIX = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                final List<String> lore2 = new ArrayList<>();
                lore2.add("\u00a78● \u00a79Kills\u00a78: \u00a77"
                        + StatsAPI.getKills(p.getUniqueId().toString(), p.getName()));
                lore2.add("\u00a78● \u00a79Gewonnen\u00a78: \u00a77"
                        + StatsAPI.getWins(p.getUniqueId().toString(), p.getName()));
                lore2.add("\u00a78● \u00a79Tode\u00a78: \u00a77"
                        + StatsAPI.getDeaths(p.getUniqueId().toString(), p.getName()));
                if (deaths3 > 1.0) {
                    lore2.add("\u00a78● \u00a79KD/R\u00a78: \u00a77" + Kdr3);
                } else {
                    lore2.add("\u00a78● \u00a79KD/R\u00a78: \u00a77-");
                }
                final SkullMeta SI1Meta2 = (SkullMeta) SIX.getItemMeta();
                SI1Meta2.setOwner(p.getName());
                SI1Meta2.setDisplayName("\u00a7e" + p.getName());
                SI1Meta2.setLore(lore2);
                SIX.setItemMeta(SI1Meta2);
                inv.setItem(0, SIX);
                p.openInventory(inv);
                final ItemStack Global2 = StackManager.Skull("\u00a78● \u00a7bGLOBAL WINS \u00a78●",
                        Material.SKULL_ITEM, "\u00a77Klicke zum anzeigen", 1, (short) 3, "Kevos");
                inv.setItem(8, Global2);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public void RoundTimer() {
        final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleAsyncRepeatingTask(this, () -> {
            --Duell.runde;
            if (Duell.runde == 60) {
                Bukkit.broadcastMessage(Data.DUELL_PREFIX
                        + "\u00a77Der Server startet in \u00a7c60 \u00a77Sekunden neu.");
            }
            if (Duell.runde == 30) {
                Bukkit.broadcastMessage(Data.DUELL_PREFIX
                        + "\u00a77Der Server startet in \u00a7c30 \u00a77Sekunden neu.");
            }
            if (Duell.runde == 10) {
                Bukkit.broadcastMessage(Data.DUELL_PREFIX
                        + "\u00a77Der Server startet in \u00a7c10 \u00a77Sekunden neu.");
            }
            if (Duell.runde == 0) {
                Bukkit.shutdown();
            }
        }, 20L, 20L);
    }
}
