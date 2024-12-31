package de.Blockcraft.BungeeSystem;

import de.Blockcraft.BungeeSystem.Commands.*;
import de.Blockcraft.BungeeSystem.Listener.CListener;
import de.Blockcraft.BungeeSystem.Listener.JoinListener;
import de.Blockcraft.BungeeSystem.Listener.PlayerLogin;
import de.Blockcraft.BungeeSystem.MOTD.Listener_Motds;
import de.Blockcraft.BungeeSystem.MOTD.Manager_Chat;
import de.Blockcraft.BungeeSystem.MOTD.MotdCMD;
import de.Blockcraft.BungeeSystem.MOTD.SetMaxCMD;
import de.Blockcraft.BungeeSystem.Ranks.Database;
import de.Blockcraft.BungeeSystem.StrikeSystem.DelStrikeCMD;
import de.Blockcraft.BungeeSystem.StrikeSystem.ShowStrikesCMD;
import de.Blockcraft.BungeeSystem.StrikeSystem.StrikeCMD;
import de.Blockcraft.BungeeSystem.StrikeSystem.StrikesCMD;
import de.Blockcraft.BungeeSystem.Util.Files;
import de.Blockcraft.HubAPI.HubCMD;
import de.Blockcraft.Party.Events;
import de.Blockcraft.Party.PartyCMD;
import de.Blockcraft.Party.PartyChatCMD;
import de.Blockcraft.Warns.ChatListener;
import de.Blockcraft.Warns.WarnCMD;
import de.Blockcraft.Warns.WarnsCMD;
import de.Blockcraft.Warns.WerbungListCMD;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

public class Main
        extends Plugin {
    public static final Database dbConfig;
    public static Main instance;
    public static int intege;
    public static File playerFile;
    public static Configuration playerConf;

    static {
        dbConfig = null;
        intege = 0;
    }

    final PluginManager pluginManager = BungeeCord.getInstance().getPluginManager();
    private final List<String> prohibitedWords = Arrays.asList(
            "noob", "l2p", "ficken", "hurensohn", "missgeburt", "bastard",
            "basti", "spast", "spasti", "huren", "huso", "l2p"
    );
    private final List<String> prohibitedAds = Arrays.asList(
            ".arpa", ":2", ".to", ".tk", ".eu", ".me", ".biz", ".com", ".info",
            ".name", ".net", ".org", ".pro", ".aero", ".asia", ".cat", ".coop",
            ".edu", ".gov", ".int", ".jobs", ".mil", ".mobi", ".museum", ".post",
            ".tel", ".de", ".travel", ".xxx", "joint", ".gs", "kommt alle auf",
            "(.)", "(Punkt)", "gommehd", "revayd", "sevenpvp", "lightningpvp",
            "cytooxien", "rewinside", ".n e t", "n e t", "d e", ",net", ",de",
            ",com", ",tk", ", n e t"
    );
    private final List<String> prohibitedPlugins = Arrays.asList(
            "/plugins", "/demote", "/promote", "/permissionsex:", "/pex", "/pl",
            "/?", "/help", "/bungee", "/me", "/bukkit:plugins", "/bukkit:pl",
            "/bukkit:?", "/bukkit:help", "/bukkit:me", "/version", "/tell",
            "/minecraft:me"
    );

    @SuppressWarnings("deprecation")
    public void onEnable() {
        instance = this;
        de.Blockcraft.BungeeSystem.MOTD.Config.loadConfig(this);

        loadConfigurations();
        registerCommands();
        registerListeners();

        Data.initUnbannable();
        Timer timer = new Timer();
        Calendar date = Calendar.getInstance();
        date.set(10, 0);
        date.set(12, 0);
        date.set(13, 0);
        date.set(14, 0);
        timer.schedule(new TimerTask(), date.getTime(), 86400000);
        ProxyServer.getInstance().getScheduler().schedule(this, () -> {
            Main.intege = Main.intege == 5 ? 0 : Main.intege + 1;
            if (Main.intege == 0) {
                for (ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
                    all.sendMessage(" ");
                    all.sendMessage("          \u00a77Unsere \u00a7aTeamSpeak\u00a77 IP:");
                    all.sendMessage("               \u00a7a" + Data.TS);
                    all.sendMessage(" ");
                }
            } else if (Main.intege == 1) {
                for (ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
                    all.sendMessage(" ");
                    all.sendMessage("          \u00a77Interesse am \u00a75YouTuber \u00a77Rang?");
                    all.sendMessage("                  \u00a7a/youtuber");
                    all.sendMessage(" ");
                }
            } else if (Main.intege == 2) {
                for (ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
                    all.sendMessage(" ");
                    all.sendMessage("           \u00a77Unser \u00a7aUnser \u00a77Discord\\u00a77 IP:");
                    all.sendMessage("                  \u00a7a" + Data.DISCORD);
                    all.sendMessage(" ");
                }
            } else if (Main.intege == 3) {
                for (ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
                    all.sendMessage(" ");
                    all.sendMessage("           \u00a77Interesse an einem Rang (\u00a76Premium\u00a77\u00a77)?");
                    all.sendMessage("                   \u00a77Besuche \u00a7a" + Data.SHOP);
                    all.sendMessage(" ");
                }
            } else if (Main.intege == 4) {
                for (ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
                    all.sendMessage(" ");
                    all.sendMessage("           \u00a77Du hast einen \u00a7cHacker \u00a77gesehen?");
                    all.sendMessage("                   \u00a77Reporte ihn mit \u00a7b/report <Spieler> <Grund>");
                    all.sendMessage(" ");
                }
            } else {
                for (ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
                    all.sendMessage(" ");
                    all.sendMessage("          \u00a77Du hast \u00a7cFragen \u00a77oder \u00a7cProbleme?");
                    all.sendMessage("               \u00a77 Nutze \u00a7a/support");
                    all.sendMessage(" ");
                }
            }
        }, 0, 5, TimeUnit.MINUTES);
        Listener_Motds.LoadMotds();
        BungeeCord.getInstance().getConsole().sendMessage("BungeeCordSystem wurde geladen");
    }

    private void loadConfigurations() {
        try {
            this.createFolders();

            Files.setDefaultConfig();

            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdir();
            }

            playerFile = new File(getDataFolder().getPath(), "playerconf.yml");
            if (!playerFile.exists()) {
                playerFile.createNewFile();
                playerFile = new File(dataFolder, "playerconf.yml");

                playerConf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(playerFile);
                playerConf.set("Amount", 0);
                ConfigurationProvider.getProvider(YamlConfiguration.class).save(playerConf, playerFile);
            } else {
                playerConf = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(this.getDataFolder().getPath(), "playerconf.yml"));
            }
        } catch (IOException e) {
            System.err.println("Error not create File config.yml");
        }
    }

    private void registerCommands() {
        // Hub Commands
        pluginManager.registerCommand(this, new HubCMD("hub"));
        pluginManager.registerCommand(this, new HubCMD("h"));
        pluginManager.registerCommand(this, new HubCMD("lobby"));
        pluginManager.registerCommand(this, new HubCMD("l"));

        // Admin Commands
        pluginManager.registerCommand(this, new AddPremium());
        pluginManager.registerCommand(this, new Strafe());
        pluginManager.registerCommand(this, new Ban());
        pluginManager.registerCommand(this, new UnBan());
        pluginManager.registerCommand(this, new TempBan());
        pluginManager.registerCommand(this, new BanList());
        pluginManager.registerCommand(this, new Check());
        pluginManager.registerCommand(this, new Broadcast());
        pluginManager.registerCommand(this, new Mute());
        pluginManager.registerCommand(this, new UnMute());
        pluginManager.registerCommand(this, new TempMute());
        pluginManager.registerCommand(this, new MuteList());
        pluginManager.registerCommand(this, new Kick());

        // Party Commands
        pluginManager.registerCommand(this, new PartyCMD());
        pluginManager.registerCommand(this, new PartyChatCMD());

        // Strike Commands
        pluginManager.registerCommand(this, new StrikeCMD("strike"));
        pluginManager.registerCommand(this, new StrikesCMD("strikes"));
        pluginManager.registerCommand(this, new DelStrikeCMD("delstrike"));
        pluginManager.registerCommand(this, new ShowStrikesCMD("showstrikes"));

        // MOTD Commands
        pluginManager.registerCommand(this, new MotdCMD("motd"));
        pluginManager.registerCommand(this, new SetMaxCMD("setmax"));

        // Warn Commands
        pluginManager.registerCommand(this, new WarnCMD());
        pluginManager.registerCommand(this, new WarnsCMD());
        pluginManager.registerCommand(this, new WerbungListCMD());

        // Miscellaneous Commands
        pluginManager.registerCommand(this, new YouTuber("youtuber"));
        pluginManager.registerCommand(this, new YouTuber("youtube"));
        pluginManager.registerCommand(this, new YouTuber("yt"));
        pluginManager.registerCommand(this, new Ping());
        pluginManager.registerCommand(this, new Find());
        pluginManager.registerCommand(this, new Globalmute());
        pluginManager.registerCommand(this, new Goto());
        pluginManager.registerCommand(this, new ClearChat("cc"));
        pluginManager.registerCommand(this, new ClearChat("clearchat"));
        pluginManager.registerCommand(this, new Restart());
        pluginManager.registerCommand(this, new Wartungen());
        pluginManager.registerCommand(this, new TeamChat());
        pluginManager.registerCommand(this, new JoinMe());
        pluginManager.registerCommand(this, new ServerJoinCommand("serverjoin"));
        //pluginManager.registerCommand(this, new Report());
        //pluginManager.registerCommand(this, new Reports());
        pluginManager.registerCommand(this, new ListCommand());
        pluginManager.registerCommand(this, new Premium("premium"));
        pluginManager.registerCommand(this, new Premium("shop"));
        pluginManager.registerCommand(this, new Support());
        pluginManager.registerCommand(this, new Mod());
        pluginManager.registerCommand(this, new Online());
    }

    private void registerListeners() {
        pluginManager.registerListener(this, new PlayerLogin());
        pluginManager.registerListener(this, new CListener());
        pluginManager.registerListener(this, new ChatListener());
        pluginManager.registerListener(this, new Listener_Motds());
        pluginManager.registerListener(this, new Manager_Chat());
        pluginManager.registerListener(this, new JoinListener());
        pluginManager.registerListener(this, new Events(this));
    }

    @SuppressWarnings("deprecation")
    public void onDisable() {
        BungeeCord.getInstance().getConsole().sendMessage("BungeeCordSystem wurde deaktiviert.");
    }

    public void createFolders() {
        if (!this.getDataFolder().exists()) {
            this.getDataFolder().mkdir();
            getLogger().info("§7Ordner erstellt! " + this.getDataFolder().getAbsolutePath());
        }

        File dataFolder = getDataFolder();

        Files.BanFile = new File(dataFolder, "bans.yml");
        Files.MuteFile = new File(dataFolder, "mutes.yml");
        Files.MsgFile = new File(dataFolder, "strike.yml");

        createFile(Files.BanFile);
        createFile(Files.MuteFile);
        createFile(Files.MsgFile);


        try {
            Files.MuteConfig = ConfigurationProvider.getProvider(YamlConfiguration.class).load(Files.MuteFile);
            Files.BanConfig = ConfigurationProvider.getProvider(YamlConfiguration.class).load(Files.BanFile);
            Files.MessagesConfiguration = ConfigurationProvider.getProvider(YamlConfiguration.class).load(Files.MsgFile);
        } catch (IOException e) {
            getLogger().severe("Fehler beim Laden der YAML-Konfigurationen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createFile(File file) {
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Fehler beim Erstellen der Datei " + file.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public List<String> getProhibitedAds() {
        return this.prohibitedAds;
    }

    public List<String> getProhibitedPlugins() {
        return this.prohibitedPlugins;
    }

    public List<String> getProhibitedWords() {
        return this.prohibitedWords;
    }

}

