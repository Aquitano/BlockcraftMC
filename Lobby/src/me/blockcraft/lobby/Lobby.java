package me.blockcraft.lobby;

import me.blockcraft.lobby.commands.CommandGlobalMute;
import me.blockcraft.lobby.commands.CommandSetholo;
import me.blockcraft.lobby.listeners.*;
import me.blockcraft.lobby.secrets.Secrets;
import me.blockcraft.lobby.utils.Board;
import me.blockcraft.lobby.utils.Inventar;
import me.blockcraft.lobby.utils.PetManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.util.*;

public class Lobby extends JavaPlugin implements Listener, PluginMessageListener {
    public static int i;
    public static int Y;
    public static int i1;
    public static int i2;
    public static int i3;
    public static boolean round;
    public static File Hemd;
    public static FileConfiguration HemdBank;
    public static File Head;
    public static FileConfiguration Heads;
    public static ArrayList<Player> Zauberei;
    public static ArrayList<Player> Ender;
    public static ArrayList<Player> Feuer;
    public static ArrayList<Player> Feuerwerk;
    public static ArrayList<Player> Feuerwerkuser;
    public static ArrayList<Player> PetOwner;

    static {
        Lobby.i = 500;
        Lobby.Y = 500;
        Lobby.i1 = 0;
        Lobby.i2 = 0;
        Lobby.i3 = 0;
        Lobby.round = false;
        Lobby.Hemd = new File("plugins/Lobby", "Hemd.yml");
        Lobby.HemdBank = YamlConfiguration.loadConfiguration(Lobby.Hemd);
        Lobby.Head = new File("plugins/Lobby", "Heads.yml");
        Lobby.Heads = YamlConfiguration.loadConfiguration(Lobby.Head);
        Lobby.Zauberei = new ArrayList<>();
        Lobby.Ender = new ArrayList<>();
        Lobby.Feuer = new ArrayList<>();
        Lobby.Feuerwerk = new ArrayList<>();
        Lobby.Feuerwerkuser = new ArrayList<>();
        Lobby.PetOwner = new ArrayList<>();
    }

    public String x;
    public String y;
    public String z;
    public String v;
    public String f;
    public String f1;
    public String f2;
    public boolean mute;
    Map<UUID, Long> cooldowns;

    public Lobby() {
        this.mute = false;
        this.cooldowns = new HashMap<>();
    }

    public void addCooldown(final Player player) {
        final long time = System.currentTimeMillis();
        this.cooldowns.put(player.getUniqueId(), time);
    }

    public void removeCooldown(final Player player) {
        this.cooldowns.remove(player.getUniqueId());
    }

    public boolean hasCooldown(final Player player) {
        if (!this.cooldowns.containsKey(player.getUniqueId())) {
            return false;
        }
        final int cooldown = 3000;
        final long now = System.currentTimeMillis();
        final long time = this.cooldowns.get(player.getUniqueId());
        if (now - time >= cooldown) {
            this.cooldowns.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void onEnable() {
        this.x = this.getConfig().getString("InvHead").replace("&", "\u00a7").replace("%", "●");
        this.y = this.getConfig().getString("InvsHead").replace("&", "\u00a7").replace("%", "●");
        this.z = this.getConfig().getString("Secrethead").replace("&", "\u00a7").replace("%", "●");
        this.v = this.getConfig().getString("Special").replace("&", "\u00a7").replace("%", "●");
        this.f = this.getConfig().getString("Hemd").replace("&", "\u00a7").replace("%", "●");
        this.f1 = this.getConfig().getString("Extras").replace("&", "\u00a7").replace("%", "●");
        this.f2 = this.getConfig().getString("Kepfe").replace("&", "\u00a7").replace("%", "●").replace("!1", "ö");

        Board.setupTab();
        System.out.println("[LOBBY] Plugin gestartet!");

        this.getServer().getPluginManager().registerEvents(this, this);
        this.getServer().getPluginManager().registerEvents(new JoinEvent(this), this);
        this.getServer().getPluginManager().registerEvents(new CompassClickEvent(this), this);
        this.getServer().getPluginManager().registerEvents(new DisableFoodLevel(), this);
        this.getServer().getPluginManager().registerEvents(new RunMessageofDay(), this);
        this.getServer().getPluginManager().registerEvents(new PlayerDeath(), this);
        this.getServer().getPluginManager().registerEvents(new Respawn(this), this);
        this.getServer().getPluginManager().registerEvents(new EntityDamager(), this);
        this.getServer().getPluginManager().registerEvents(new DoubleJump(this), this);
        this.getServer().getPluginManager().registerEvents(new QuitEvent(), this);
        this.getServer().getPluginManager().registerEvents(new EntityDeath(), this);
        this.getServer().getPluginManager().registerEvents(new AntiBuild(), this);
        this.getServer().getPluginManager().registerEvents(new DisableCMD(this), this);
        this.getServer().getPluginManager().registerEvents(new EntityHit(), this);
        this.getServer().getPluginManager().registerEvents(new UnleashEvent(), this);
        this.getServer().getPluginManager().registerEvents(new PlayerHider(this), this);
        this.getServer().getPluginManager().registerEvents(new Secrets(this), this);
        this.getServer().getPluginManager().registerEvents(new ChatMute(this), this);
        this.getServer().getPluginManager().registerEvents(new BlazeClick(this), this);
        this.getServer().getPluginManager().registerEvents(new PetManager(this), this);
        this.getServer().getPluginManager().registerEvents(new MoveListener(this), this);
        this.getServer().getPluginManager().registerEvents(new PremiumInvListener(this), this);
        this.getServer().getPluginManager().registerEvents(new ItemDropListener(), this);
        this.getServer().getPluginManager().registerEvents(new InventoryMoveListener(), this);
        this.getCommand("setholo").setExecutor(new CommandSetholo(this));
        this.getCommand("globalmute").setExecutor(new CommandGlobalMute(this));
        this.getServer().getMessenger().registerIncomingPluginChannel(this, "WDL|INIT", this);
        this.getServer().getMessenger().registerOutgoingPluginChannel(this, "WDL|CONTROL");
        this.loadConfig();
        this.saveConfig();
        this.mute = false;
        this.RefreshBlaze();
        this.getBlaze().getChunk().load();
        final Villager g4 = (Villager) this.getBlaze().getWorld().spawnEntity(this.getBlaze(), EntityType.VILLAGER);
        g4.spigot().isInvulnerable();
        g4.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255), false);
        g4.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, -25000));
        Location loc;
        final World world = this.getBlaze().getWorld();
        final double x = g4.getLocation().getX();
        final double y = g4.getLocation().getY() + 1.3;
        final double z = g4.getLocation().getZ();
        final float yaw = g4.getLocation().getYaw();
        final float pitch = g4.getLocation().getPitch();
        loc = new Location(world, x, y, z, yaw, pitch);
        Location loc2;
        final World world2 = this.getBlaze().getWorld();
        final double x2 = g4.getLocation().getX();
        final double y2 = g4.getLocation().getY() + 1.0;
        final double z2 = g4.getLocation().getZ();
        final float yaw2 = g4.getLocation().getYaw();
        final float pitch2 = g4.getLocation().getPitch();
        loc2 = new Location(world2, x2, y2, z2, yaw2, pitch2);
        final ArmorStand g51x = (ArmorStand) world2.spawnEntity(loc2, EntityType.ARMOR_STAND);
        g51x.setVisible(false);
        g51x.setCustomNameVisible(true);
        g51x.setSmall(true);
        g51x.setCustomName("\u00a77Kaufe dir coole Items!");
        g51x.setGravity(false);
        final ArmorStand g51xs = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        g51xs.setVisible(false);
        g51xs.setCustomNameVisible(true);
        g51xs.setSmall(true);
        g51xs.setCustomName("\u00a78● \u00a79LOBBY-SHOP \u00a78●");
        g51xs.setGravity(false);
        this.getBlaze().getChunk().load();
    }

    public void onDisable() {
        System.out.println("[LOBBY] Plugin gestoppt");
        this.getServer().getMessenger().unregisterIncomingPluginChannel(this, "WDL|INIT");
        this.getServer().getMessenger().unregisterOutgoingPluginChannel(this, "WDL|CONTROL");
        for (final Entity entity : this.getSpawn().getWorld().getEntities()) {
            if (entity.getType() != EntityType.PLAYER && entity.getType() != EntityType.ITEM_FRAME
                    && entity.getType() != EntityType.MINECART) {
                entity.remove();
            }
        }
    }

    public void loadConfig() {
        final FileConfiguration cfg = this.getConfig();
        cfg.options().copyDefaults(true);
    }

    public void onPluginMessageReceived(final String channel, final Player player, final byte[] data) {
        if (channel.equals("WDL|INIT") && !player.hasPermission("antiwdl.bypass")) {
            player.kickPlayer("\u00a7cWorld Downloader ist nicht erlaubt.");
        }
    }

    public boolean onCommand(final CommandSender sender, final Command cmd, final String commandLabel,
                             final String[] args) {
        final Player p = (Player) sender;
        if (cmd.getName().equalsIgnoreCase("setspawn")) {
            if (p.hasPermission("bs.setspawn") && args.length == 1) {
                if (args[0].equalsIgnoreCase("spawn")) {
                    this.getConfig().set("spawn.world", p.getLocation().getWorld().getName());
                    this.getConfig().set("spawn.x", p.getLocation().getX());
                    this.getConfig().set("spawn.y", p.getLocation().getY());
                    this.getConfig().set("spawn.z", p.getLocation().getZ());
                    this.getConfig().set("spawn.yaw", p.getLocation().getYaw());
                    this.getConfig().set("spawn.pitch", p.getLocation().getPitch());
                    p.sendMessage("\u00a7cServer \u00a78» \u00a77gesetzt.");
                    this.saveConfig();
                } else if (args[0].equalsIgnoreCase("Duell")) {
                    this.getConfig().set("syndicate.world", p.getLocation().getWorld().getName());
                    this.getConfig().set("syndicate.x", p.getLocation().getX());
                    this.getConfig().set("syndicate.y", p.getLocation().getY());
                    this.getConfig().set("syndicate.z", p.getLocation().getZ());
                    this.getConfig().set("syndicate.yaw", p.getLocation().getYaw());
                    this.getConfig().set("syndicate.pitch", p.getLocation().getPitch());
                    p.sendMessage("\u00a7cServer \u00a78» \u00a77gesetzt.");
                    this.saveConfig();
                } else if (args[0].equalsIgnoreCase("Slimebrawl")) {
                    this.getConfig().set("superjump.world", p.getLocation().getWorld().getName());
                    this.getConfig().set("superjump.x", p.getLocation().getX());
                    this.getConfig().set("superjump.y", p.getLocation().getY());
                    this.getConfig().set("superjump.z", p.getLocation().getZ());
                    this.getConfig().set("superjump.yaw", p.getLocation().getYaw());
                    this.getConfig().set("superjump.pitch", p.getLocation().getPitch());
                    p.sendMessage("\u00a7cServer \u00a78» \u00a77gesetzt.");
                    this.saveConfig();
                } else if (args[0].equalsIgnoreCase("jump")) {
                    this.getConfig().set("skypvp.world", p.getLocation().getWorld().getName());
                    this.getConfig().set("skypvp.x", p.getLocation().getX());
                    this.getConfig().set("skypvp.y", p.getLocation().getY());
                    this.getConfig().set("skypvp.z", p.getLocation().getZ());
                    this.getConfig().set("skypvp.yaw", p.getLocation().getYaw());
                    this.getConfig().set("skypvp.pitch", p.getLocation().getPitch());
                    p.sendMessage("\u00a7cServer \u00a78» \u00a77gesetzt.");
                    this.saveConfig();
                } else if (args[0].equalsIgnoreCase("rush")) {
                    this.getConfig().set("trapsg.world", p.getLocation().getWorld().getName());
                    this.getConfig().set("trapsg.x", p.getLocation().getX());
                    this.getConfig().set("trapsg.y", p.getLocation().getY());
                    this.getConfig().set("trapsg.z", p.getLocation().getZ());
                    this.getConfig().set("trapsg.yaw", p.getLocation().getYaw());
                    this.getConfig().set("trapsg.pitch", p.getLocation().getPitch());
                    p.sendMessage("\u00a7cServer \u00a78» \u00a77 gesetzt.");
                    this.saveConfig();
                } else if (args[0].equalsIgnoreCase("shop")) {
                    this.getConfig().set("hol.world", p.getLocation().getWorld().getName());
                    this.getConfig().set("hol.x", p.getLocation().getX());
                    this.getConfig().set("hol.y", p.getLocation().getY());
                    this.getConfig().set("hol.z", p.getLocation().getZ());
                    this.getConfig().set("hol.yaw", p.getLocation().getYaw());
                    this.getConfig().set("hol.pitch", p.getLocation().getPitch());
                    p.sendMessage("\u00a7cServer \u00a78» \u00a77 gesetzt.");
                    this.saveConfig();
                } else if (args[0].equalsIgnoreCase("premium")) {
                    this.getConfig().set("premium.world", p.getLocation().getWorld().getName());
                    this.getConfig().set("premium.x", p.getLocation().getX());
                    this.getConfig().set("premium.y", p.getLocation().getY());
                    this.getConfig().set("premium.z", p.getLocation().getZ());
                    this.getConfig().set("premium.yaw", p.getLocation().getYaw());
                    this.getConfig().set("premium.pitch", p.getLocation().getPitch());
                    p.sendMessage("\u00a7cServer \u00a78» \u00a77 gesetzt.");
                    this.saveConfig();
                }
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

    public Location getShop() {
        Location loc;
        final String world = this.getConfig().getString("hol.world");
        final double x = this.getConfig().getDouble("hol.x");
        final double y = this.getConfig().getDouble("hol.y");
        final double z = this.getConfig().getDouble("hol.z");
        final float yaw = (float) this.getConfig().getDouble("hol.yaw");
        final float pitch = (float) this.getConfig().getDouble("hol.pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public Location getSlimebrawl() {
        Location loc;
        final String world = this.getConfig().getString("superjump.world");
        final double x = this.getConfig().getDouble("superjump.x");
        final double y = this.getConfig().getDouble("superjump.y");
        final double z = this.getConfig().getDouble("superjump.z");
        final float yaw = (float) this.getConfig().getDouble("superjump.yaw");
        final float pitch = (float) this.getConfig().getDouble("superjump.pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public Location getBlaze() {
        Location loc;
        final String world = this.getConfig().getString("blaze.world");
        final double x = this.getConfig().getDouble("blaze.x");
        final double y = this.getConfig().getDouble("blaze.y");
        final double z = this.getConfig().getDouble("blaze.z");
        final float yaw = (float) this.getConfig().getDouble("blaze.yaw");
        final float pitch = (float) this.getConfig().getDouble("blaze.pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public Location getDuell() {
        Location loc;
        final String world = this.getConfig().getString("syndicate.world");
        final double x = this.getConfig().getDouble("syndicate.x");
        final double y = this.getConfig().getDouble("syndicate.y");
        final double z = this.getConfig().getDouble("syndicate.z");
        final float yaw = (float) this.getConfig().getDouble("syndicate.yaw");
        final float pitch = (float) this.getConfig().getDouble("syndicate.pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public Location getPremium() {
        Location loc;
        final String world = this.getConfig().getString("premium.world");
        final double x = this.getConfig().getDouble("premium.x");
        final double y = this.getConfig().getDouble("premium.y");
        final double z = this.getConfig().getDouble("premium.z");
        final float yaw = (float) this.getConfig().getDouble("premium.yaw");
        final float pitch = (float) this.getConfig().getDouble("premium.pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public Location getJump() {
        Location loc;
        final String world = this.getConfig().getString("skypvp.world");
        final double x = this.getConfig().getDouble("skypvp.x");
        final double y = this.getConfig().getDouble("skypvp.y");
        final double z = this.getConfig().getDouble("skypvp.z");
        final float yaw = (float) this.getConfig().getDouble("skypvp.yaw");
        final float pitch = (float) this.getConfig().getDouble("skypvp.pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public Location getTW() {
        Location loc;
        final String world = this.getConfig().getString("trapsg.world");
        final double x = this.getConfig().getDouble("trapsg.x");
        final double y = this.getConfig().getDouble("trapsg.y");
        final double z = this.getConfig().getDouble("trapsg.z");
        final float yaw = (float) this.getConfig().getDouble("trapsg.yaw");
        final float pitch = (float) this.getConfig().getDouble("trapsg.pitch");
        loc = new Location(Bukkit.getWorld(world), x, y, z, yaw, pitch);
        return loc;
    }

    public ItemStack Stack(final String Display, final Material m, final String lores, final int Anzahl,
                           final short Shorts) {
        return Inventar.Stack(Display, m, lores, Anzahl, Shorts);
    }

    public ItemStack Skull(final String Display, final Material m, final String lores, final int Anzahl,
                           final short Shorts, final String Owner) {
        final ItemStack stack = new ItemStack(m, Anzahl, (short) 3);
        final SkullMeta meta = (SkullMeta) Bukkit.getItemFactory().getItemMeta(Material.SKULL_ITEM);
        meta.setOwner(Owner);
        meta.setDisplayName(Display);
        final List<String> lore = new ArrayList<>();
        lore.add(lores);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack colorArmor(final String Display, final Material m, final String lores, final int Anzahl,
                                final short Shorts, final int RGB1, final int RGB2, final int RGB3) {
        final ItemStack istack52 = new ItemStack(m, Anzahl, Shorts);
        final LeatherArmorMeta istackMeta52 = (LeatherArmorMeta) istack52.getItemMeta();
        istackMeta52.setColor(Color.fromRGB(RGB1, RGB2, RGB3));
        istackMeta52.setDisplayName(Display);
        final List<String> lore = new ArrayList<>();
        lore.add(lores);
        istackMeta52.setLore(lore);
        istack52.setItemMeta(istackMeta52);
        return istack52;
    }

    public void openShop(final Player p) {
        final Inventory inv = Bukkit.createInventory(null, 9, this.v);
        final ItemStack i = this.Stack("\u00a78» \u00a79Köpfe", Material.SKULL_ITEM,
                "\u00a78● \u00a77Kaufe dir Köpfe!", 1, (short) 3);
        final ItemStack i2 = this.Stack("\u00a79Premium", Material.SPECKLED_MELON, "\u00a78● \u00a77Kaufe dir Premium!",
                1, (short) 0);
        final ItemStack rot = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
        final LeatherArmorMeta lch = (LeatherArmorMeta) rot.getItemMeta();
        lch.setColor(Color.fromRGB(0, 102, 204));
        lch.setDisplayName("\u00a78» \u00a79Hemden");
        final List<String> lore11 = new ArrayList<>();
        lore11.add("\u00a78● \u00a77Kaufe dir Hemden!");
        lch.setLore(lore11);
        rot.setItemMeta(lch);
        final ItemStack istack541 = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 15);
        final ItemMeta istackMeta541 = istack541.getItemMeta();
        istackMeta541.setDisplayName(" ");
        istack541.setItemMeta(istackMeta541);
        inv.setItem(0, istack541);
        inv.setItem(2, istack541);
        inv.setItem(3, istack541);
        inv.setItem(5, istack541);
        inv.setItem(6, istack541);
        inv.setItem(8, istack541);
        inv.setItem(1, i);
        inv.setItem(4, rot);
        inv.setItem(7, i2);
        p.openInventory(inv);
    }

    public void openExtraInv(final Player p) {
        final Inventory inv = Bukkit.createInventory(null, 9, this.f1);
        final ItemStack i = this.Stack("\u00a76laufende Köpfe", Material.SKULL_ITEM,
                "\u00a78» \u00a77Kaufe dir laufende Köpfe!", 1, (short) 4);
        inv.setItem(0, i);
    }

    public void openHeadInv(final Player p) {
        final boolean ax = Lobby.Heads.getBoolean((p.getName()) + ".a");
        final boolean abx = Lobby.Heads.getBoolean((p.getName()) + ".ab");
        final boolean absx = Lobby.Heads.getBoolean((p.getName()) + ".abs");
        final boolean abssx = Lobby.Heads.getBoolean((p.getName()) + ".abss");
        final boolean disx = Lobby.Heads.getBoolean((p.getName()) + ".dis");
        final boolean dissx = Lobby.Heads.getBoolean((p.getName()) + ".diss");
        final boolean disssx = Lobby.Heads.getBoolean((p.getName()) + ".disss");
        final boolean footx = Lobby.Heads.getBoolean((p.getName()) + ".foot");
        final boolean footsx = Lobby.Heads.getBoolean((p.getName()) + ".foots");
        final boolean mariosx = Lobby.Heads.getBoolean((p.getName()) + ".marios");
        final boolean yoshix = Lobby.Heads.getBoolean((p.getName()) + ".yoshi");
        final boolean yoshisx = Lobby.Heads.getBoolean((p.getName()) + ".yoshis");
        final boolean trooperx = Lobby.Heads.getBoolean((p.getName()) + ".trooper");
        final boolean Bobax = Lobby.Heads.getBoolean((p.getName()) + ".boba");
        final boolean Chiefx = Lobby.Heads.getBoolean((p.getName()) + ".chief");
        final boolean Ironx = Lobby.Heads.getBoolean((p.getName()) + ".iron");
        final boolean CapAmx = Lobby.Heads.getBoolean((p.getName()) + ".capam");
        final boolean Crumpx = Lobby.Heads.getBoolean((p.getName()) + ".crump");
        final Inventory inv = Bukkit.createInventory(null, 27, this.f2);
        final ItemStack a = this.Skull("\u00a79Hamburger Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "Sloggy_Whopper");
        final ItemStack ab = this.Skull("\u00a79Kisten Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "Zealock");
        final ItemStack abs = this.Skull("\u00a79Piston Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "SkillOuTes");
        final ItemStack abss = this.Skull("\u00a79Command Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "ExtrayeaMC");
        final ItemStack dis = this.Skull("\u00a79Dispenser Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "TreePuncher105");
        final ItemStack diss = this.Skull("\u00a79Gummi Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "Trajan");
        final ItemStack disss = this.Skull("\u00a79TNT Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "Eternal");
        final ItemStack foot = this.Skull("\u00a79Football Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "Smaug");
        final ItemStack foots = this.Skull("\u00a79Ender Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "Edna_I");
        final ItemStack marios = this.Skull("\u00a79SuperMario Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins",
                1, (short) 3, "Mario");
        final ItemStack yoshi = this.Skull("\u00a79Yoshis Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "Yoshi");
        final ItemStack yoshis = this.Skull("\u00a79Bowsers Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "Bowser");
        final ItemStack trooper = this.Skull("\u00a79Sturmtruppen Kopf", Material.SKULL_ITEM,
                "\u00a78» \u00a7r300 Coins", 1, (short) 3, "Stormtrooper");
        final ItemStack Boba = this.Skull("\u00a79Boba's Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "BobaFett");
        final ItemStack Chief = this.Skull("\u00a79Master Chief's Kopf", Material.SKULL_ITEM,
                "\u00a78» \u00a7r300 Coins", 1, (short) 3, "jscherry");
        final ItemStack Iron = this.Skull("\u00a79Iron Man's Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins", 1,
                (short) 3, "iron_man");
        final ItemStack CapAm = this.Skull("\u00a79Captain America's Kopf", Material.SKULL_ITEM,
                "\u00a78» \u00a7r300 Coins", 1, (short) 3, "MURICA");
        final ItemStack Crump = this.Skull("\u00a79Crumpy Cat's Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7r300 Coins",
                1, (short) 3, "GrumpyCat");
        final ItemStack av = this.Skull("\u00a79Hamburger Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "Sloggy_Whopper");
        final ItemStack abv = this.Skull("\u00a79Kisten Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "Zealock");
        final ItemStack absv = this.Skull("\u00a79Piston Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "SkillOuTes");
        final ItemStack abssv = this.Skull("\u00a79Command Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "ExtrayeaMC");
        final ItemStack disv = this.Skull("\u00a79Dispenser Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "TreePuncher105");
        final ItemStack dissv = this.Skull("\u00a79Gummi Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "Trajan");
        final ItemStack disssv = this.Skull("\u00a79TNT Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "Eternal");
        final ItemStack footv = this.Skull("\u00a79Football Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "Smaug");
        final ItemStack footsv = this.Skull("\u00a79Ender Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "Edna_I");
        final ItemStack mariosv = this.Skull("\u00a79SuperMario Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!",
                1, (short) 3, "Mario");
        final ItemStack yoshiv = this.Skull("\u00a79Yoshis Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "Yoshi");
        final ItemStack yoshisv = this.Skull("\u00a79Bowsers Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "Bowser");
        final ItemStack trooperv = this.Skull("\u00a79Sturmtruppen Kopf", Material.SKULL_ITEM,
                "\u00a78» \u00a7aGekauft!", 1, (short) 3, "Stormtrooper");
        final ItemStack Bobav = this.Skull("\u00a79Boba's Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "BobaFett");
        final ItemStack Chiefv = this.Skull("\u00a79Master Chief's Kopf", Material.SKULL_ITEM,
                "\u00a78» \u00a7aGekauft!", 1, (short) 3, "jscherry");
        final ItemStack Ironv = this.Skull("\u00a79Iron Man's Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!", 1,
                (short) 3, "iron_man");
        final ItemStack CapAmv = this.Skull("\u00a79Captain America's Kopf", Material.SKULL_ITEM,
                "\u00a78» \u00a7aGekauft!", 1, (short) 3, "MURICA");
        final ItemStack Crumpv = this.Skull("\u00a79Crumpy Cat's Kopf", Material.SKULL_ITEM, "\u00a78» \u00a7aGekauft!",
                1, (short) 3, "GrumpyCat");
        if (ax) {
            inv.setItem(2, av);
        } else {
            inv.setItem(2, a);
        }
        if (abx) {
            inv.setItem(3, abv);
        } else {
            inv.setItem(3, ab);
        }
        if (absx) {
            inv.setItem(4, absv);
        } else {
            inv.setItem(4, abs);
        }
        if (abssx) {
            inv.setItem(5, abssv);
        } else {
            inv.setItem(5, abss);
        }
        if (disx) {
            inv.setItem(6, disv);
        } else {
            inv.setItem(6, dis);
        }
        if (dissx) {
            inv.setItem(7, dissv);
        } else {
            inv.setItem(7, diss);
        }
        if (disssx) {
            inv.setItem(8, disssv);
        } else {
            inv.setItem(8, disss);
        }
        if (footx) {
            inv.setItem(9, footv);
        } else {
            inv.setItem(9, foot);
        }
        if (footsx) {
            inv.setItem(10, footsv);
        } else {
            inv.setItem(10, foots);
        }
        if (mariosx) {
            inv.setItem(11, mariosv);
        } else {
            inv.setItem(11, marios);
        }
        if (yoshix) {
            inv.setItem(12, yoshiv);
        } else {
            inv.setItem(12, yoshi);
        }
        if (yoshisx) {
            inv.setItem(13, yoshisv);
        } else {
            inv.setItem(13, yoshis);
        }
        if (trooperx) {
            inv.setItem(14, trooperv);
        } else {
            inv.setItem(14, trooper);
        }
        if (Bobax) {
            inv.setItem(15, Bobav);
        } else {
            inv.setItem(15, Boba);
        }
        if (Chiefx) {
            inv.setItem(16, Chiefv);
        } else {
            inv.setItem(16, Chief);
        }
        if (Ironx) {
            inv.setItem(17, Ironv);
        } else {
            inv.setItem(17, Iron);
        }
        if (CapAmx) {
            inv.setItem(0, CapAmv);
        } else {
            inv.setItem(0, CapAm);
        }
        if (Crumpx) {
            inv.setItem(1, Crumpv);
        } else {
            inv.setItem(1, Crump);
        }
        p.openInventory(inv);
    }

    public void openCompInv(final Player p) {
        final ItemStack istack52 = new ItemStack(Material.IRON_SWORD);
        final ItemMeta istackMeta52 = istack52.getItemMeta();
        istackMeta52.setDisplayName("\u00a78» \u00a79QSG");
        final List<String> lore = new ArrayList<>();
        lore.add("\u00a78● \u00a77Klicke zum Teleportieren.");
        istackMeta52.setLore(lore);
        istack52.setItemMeta(istackMeta52);
        final ItemStack istack53 = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
        final ItemMeta istackMeta53 = istack53.getItemMeta();
        istackMeta53.setDisplayName("\u00a78» \u00a79Duell");
        final List<String> lore2 = new ArrayList<>();
        lore2.add("\u00a78● \u00a77Klicke zum Teleportieren.");
        istackMeta53.setLore(lore2);
        istack53.setItemMeta(istackMeta53);
        final ItemStack istack54 = new ItemStack(Material.GOLD_INGOT);
        final ItemMeta istackMeta54 = istack54.getItemMeta();
        istackMeta54.setDisplayName("\u00a78» \u00a79Shop");
        final List<String> lore3 = new ArrayList<>();
        lore3.add("\u00a78● \u00a77Klicke zum Teleportieren.");
        istackMeta54.setLore(lore3);
        istack54.setItemMeta(istackMeta54);
        final ItemStack istack55 = new ItemStack(Material.EMERALD);
        final ItemMeta istackMeta55 = istack55.getItemMeta();
        istackMeta55.setDisplayName("\u00a78» \u00a79EmeraldRush");
        final List<String> lore4 = new ArrayList<>();
        lore4.add("\u00a78● \u00a77Klicke zum Teleportieren.");
        istackMeta55.setLore(lore4);
        istack55.setItemMeta(istackMeta55);
        final ItemStack istack56 = new ItemStack(Material.EYE_OF_ENDER);
        final ItemMeta istackMeta56 = istack56.getItemMeta();
        istackMeta56.setDisplayName("\u00a78» \u00a7bSpawn");
        final List<String> lore5 = new ArrayList<>();
        lore5.add("\u00a78● \u00a77Klicke zum Teleportieren.");
        istackMeta56.setLore(lore5);
        istack56.setItemMeta(istackMeta56);
        final ItemStack istack57 = new ItemStack(Material.DIAMOND_BOOTS);
        final ItemMeta istackMeta57 = istack57.getItemMeta();
        istackMeta57.setDisplayName("\u00a78» \u00a79JumpAndRun");
        final List<String> lore6 = new ArrayList<>();
        lore6.add("\u00a78● \u00a77Klicke zum Teleportieren.");
        istackMeta57.setLore(lore6);
        istack57.setItemMeta(istackMeta57);
        final ItemStack istack58 = new ItemStack(Material.IRON_FENCE, 1, (short) 0);
        final ItemMeta istackMeta58 = istack58.getItemMeta();
        istackMeta58.setDisplayName(" ");
        istack58.setItemMeta(istackMeta58);
        final Inventory inv = Bukkit.createInventory(null, 27, this.x);
        inv.setItem(2, istack52);
        inv.setItem(6, istack55);
        inv.setItem(10, istack53);
        inv.setItem(13, istack56);
        inv.setItem(16, istack54);
        inv.setItem(20, istack58);
        inv.setItem(24, istack57);
        p.openInventory(inv);
    }

    public void openChestplateShop(final Player p) {
        final Inventory inv = Bukkit.createInventory(null, 9, this.f);
        final ItemStack rot = this.colorArmor("\u00a79Rotes Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a78» \u00a7aGekauft!", 1, (short) 0, 255, 0, 0);
        final ItemStack gruen = this.colorArmor("\u00a79Grünes Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a78» \u00a7aGekauft!", 1, (short) 0, 16, 156, 0);
        final ItemStack blau = this.colorArmor("\u00a79Blaues Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a78» \u00a7aGekauft!", 1, (short) 0, 19, 79, 218);
        final ItemStack lila = this.colorArmor("\u00a79Lila Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a78» \u00a7aGekauft!", 1, (short) 0, 162, 0, 255);
        final ItemStack orange = this.colorArmor("\u00a79Oranges Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a78» \u00a7aGekauft!", 1, (short) 0, 255, 162, 0);
        final ItemStack Turkis = this.colorArmor("\u00a79Türkises Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a78» \u00a7aGekauft!", 1, (short) 0, 0, 100, 142);
        final ItemStack back = this.Stack("\u00a78» \u00a7czurück", Material.BARRIER, " ", 1, (short) 0);
        final ItemStack rotc = this.colorArmor("\u00a79Rotes Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a77Kosten \u00a78» \u00a7r500", 1, (short) 0, 255, 0, 0);
        final ItemStack gruenc = this.colorArmor("\u00a79Grünes Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a77Kosten \u00a78» \u00a7r500", 1, (short) 0, 16, 156, 0);
        final ItemStack blauc = this.colorArmor("\u00a79Blaues Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a77Kosten \u00a78» \u00a7r500", 1, (short) 0, 19, 79, 218);
        final ItemStack lilac = this.colorArmor("\u00a79Lila Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a77Kosten \u00a78» \u00a7r500", 1, (short) 0, 162, 0, 255);
        final ItemStack orangec = this.colorArmor("\u00a79Oranges Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a77Kosten \u00a78» \u00a7r500", 1, (short) 0, 255, 162, 0);
        final ItemStack Turkisc = this.colorArmor("\u00a79Türkises Hemd", Material.LEATHER_CHESTPLATE,
                "\u00a77Kosten \u00a78» \u00a7r500", 1, (short) 0, 0, 100, 142);
        final boolean red = Lobby.HemdBank.getBoolean((p.getName()) + ".Rot");
        final boolean green = Lobby.HemdBank.getBoolean((p.getName()) + ".Gruen");
        final boolean blue = Lobby.HemdBank.getBoolean((p.getName()) + ".Blau");
        final boolean purple = Lobby.HemdBank.getBoolean((p.getName()) + ".Lila");
        final boolean oranges = Lobby.HemdBank.getBoolean((p.getName()) + ".Orange");
        final boolean Turkiss = Lobby.HemdBank.getBoolean((p.getName()) + ".Turkis");
        if (red) {
            inv.setItem(0, rot);
        } else {
            inv.setItem(0, rotc);
        }
        if (green) {
            inv.setItem(1, gruen);
        } else {
            inv.setItem(1, gruenc);
        }
        if (blue) {
            inv.setItem(2, blau);
        } else {
            inv.setItem(2, blauc);
        }
        if (purple) {
            inv.setItem(3, lila);
        } else {
            inv.setItem(3, lilac);
        }
        if (oranges) {
            inv.setItem(4, orange);
        } else {
            inv.setItem(4, orangec);
        }
        if (Turkiss) {
            inv.setItem(5, Turkis);
        } else {
            inv.setItem(5, Turkisc);
        }
        inv.setItem(8, back);
        p.openInventory(inv);
    }

    public void placeBlaze() {
        for (final Entity entity : this.getSpawn().getWorld().getEntities()) {
            if (entity.getType() != EntityType.PLAYER && entity.getType() != EntityType.ITEM_FRAME
                    && entity.getType() != EntityType.MINECART) {
                entity.remove();
            }
        }
        this.getBlaze().getChunk().load();
        final Villager g4 = (Villager) this.getBlaze().getWorld().spawnEntity(this.getBlaze(), EntityType.VILLAGER);
        g4.spigot().isInvulnerable();
        g4.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 255), false);
        g4.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, -25000));
        Location loc;
        final World world = this.getBlaze().getWorld();
        final double x = g4.getLocation().getX();
        final double y = g4.getLocation().getY() + 1.3;
        final double z = g4.getLocation().getZ();
        final float yaw = g4.getLocation().getYaw();
        final float pitch = g4.getLocation().getPitch();
        loc = new Location(world, x, y, z, yaw, pitch);
        Location loc2;
        final World world2 = this.getBlaze().getWorld();
        final double x2 = g4.getLocation().getX();
        final double y2 = g4.getLocation().getY() + 1.0;
        final double z2 = g4.getLocation().getZ();
        final float yaw2 = g4.getLocation().getYaw();
        final float pitch2 = g4.getLocation().getPitch();
        loc2 = new Location(world2, x2, y2, z2, yaw2, pitch2);
        final ArmorStand g51x = (ArmorStand) world2.spawnEntity(loc2, EntityType.ARMOR_STAND);
        g51x.setVisible(false);
        g51x.setCustomNameVisible(true);
        g51x.setSmall(true);
        g51x.setCustomName("\u00a77Kaufe dir coole Items!");
        g51x.setGravity(false);
        final ArmorStand g51xs = (ArmorStand) world.spawnEntity(loc, EntityType.ARMOR_STAND);
        g51xs.setVisible(false);
        g51xs.setCustomNameVisible(true);
        g51xs.setSmall(true);
        g51xs.setCustomName("\u00a78● \u00a79LOBBY-SHOP \u00a78●");
        g51xs.setGravity(false);
        this.getBlaze().getChunk().load();
    }

    public void RefreshBlaze() {
        final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(this, () -> {
            --Lobby.Y;
            if (Lobby.Y == 0) {
                Lobby.this.getBlaze().getChunk().load();
                for (final Entity entity : Lobby.this.getSpawn().getWorld().getEntities()) {
                    if (entity.getType() != EntityType.PLAYER && entity.getType() != EntityType.ITEM_FRAME
                            && entity.getType() != EntityType.PIG && entity.getType() != EntityType.WOLF
                            && entity.getType() != EntityType.RABBIT && entity.getType() != EntityType.CHICKEN
                            && entity.getType() != EntityType.MINECART) {
                        entity.remove();
                    }
                }
                Lobby.this.placeBlaze();
                Lobby.Y = 500;
            }
        }, 20L, 20L);
    }

    public void RefreshPlayers() {
        final BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(this, () -> {
            ++Lobby.i1;
            ++Lobby.i2;
            ++Lobby.i3;
            if (Lobby.i1 == 230 | Lobby.i2 == 230 | Lobby.i3 == 230) {
                Lobby.i1 = 0;
                Lobby.i2 = 0;
                Lobby.i3 = 0;
            }
            /*
             * for (final Player all : Bukkit.getOnlinePlayers()) {
             * if (all.getName().equals("Aquitano")) {
             * final ItemStack Shoes = Lobby.this.colorArmor("", Material.LEATHER_BOOTS, "",
             * 1, (short)0, Lobby.i1, Lobby.i2, Lobby.i3);
             * all.getInventory().setBoots(Shoes);
             * final ItemStack Shoes2 = Lobby.this.colorArmor("",
             * Material.LEATHER_CHESTPLATE, "", 1, (short)0, Lobby.i1, Lobby.i2, Lobby.i3);
             * all.getInventory().setChestplate(Shoes2);
             * }
             * }
             */
        }, 1L, 1L);
    }
}
