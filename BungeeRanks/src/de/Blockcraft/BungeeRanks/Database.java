package de.Blockcraft.BungeeRanks;

import net.craftminecraft.bungee.bungeeyaml.supereasyconfig.Comment;
import net.craftminecraft.bungee.bungeeyaml.supereasyconfig.Config;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Database
        extends Config {
    @Comment("Rank: %rank%; Player: %player%; Message: %message%")
    public String Chat = "&a[%rank%&r&a] &7%player%:&r %message%";
    @Comment("Um \"UseForBukkit\" zu aktivieren, muss in allen Bukkit-Server das Plugin \"BungeeRankSystem\" hochgeladen sein.")
    public Boolean UseForBukkit = false;
    @Comment("Group-Name Group-Prefix Group-Permissions(Permission1,Permission2,Permission3,...)")
    public List<String> Permissions = new ArrayList<>();
    @Comment("Playername Rank")
    public List<String> Ranks = new ArrayList<>();

    public Database(Plugin plugin) {
        this.CONFIG_HEADER = "Configuration";
        this.CONFIG_FILE = new File(plugin.getDataFolder(), "config.yml");
        try {
            this.init();
        } catch (Exception var2_2) {
            // empty catch block
        }
    }
}

