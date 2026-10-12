package me.blockcraft.api;

import me.blockcraft.api.achievement.Achievements;
import me.blockcraft.api.afk.Afk;
import me.blockcraft.api.audit.StaffAudit;
import me.blockcraft.api.cosmetic.Effects;
import me.blockcraft.api.elo.Elo;
import me.blockcraft.api.game.RoundConfig;
import me.blockcraft.api.game.Streaks;
import me.blockcraft.api.ignore.Ignores;
import me.blockcraft.api.menu.Profiles;
import me.blockcraft.api.network.ServerGroups;
import me.blockcraft.api.network.ServerPresence;
import me.blockcraft.api.notification.Notifications;
import me.blockcraft.api.nick.NickStats;
import me.blockcraft.api.pack.ResourcePacks;
import me.blockcraft.api.player.RealNames;
import me.blockcraft.api.rank.NickRanks;
import me.blockcraft.api.proxy.NetworkOperations;
import me.blockcraft.api.settings.PlayerSettings;
import me.blockcraft.api.shop.Unlocks;
import me.blockcraft.api.task.PlayerTasks;
import me.blockcraft.api.vanish.VanishedPlayers;
import me.blockcraft.api.world.BuildMode;
import me.blockcraft.api.world.MapBundle;
import me.blockcraft.api.xp.Xp;
import me.blockcraft.api.xp.XpReward;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.coins.CoinsRepository;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRepository;
import me.blockcraft.common.round.RoundRecapRepository;
import me.blockcraft.common.shop.Shop;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Entry point for other plugins; populated while BlockcraftAPI is enabled, which builds every service.
 *
 * @param operations   typed requests to the proxy: reports and punishments, answered with a result
 * @param playerTasks  database work behind menus, delivered only to a player who is still there for it
 * @param unlocks      network-wide unlocks of cosmetics, kits, perks and secrets
 * @param shop         buys an unlock with coins, once, however many lobbies the player hops between
 * @param recaps       the recap of a player's last round, written by the game and taken by the lobby
 * @param settings     remembered per-player choices such as the chat tag or kill effect
 * @param resourcePack which clients wear the network's resource pack, and the icons each one can draw
 * @param profiles     the player's own profile card: rank, level, coins and per-game stats
 * @param effects      kill and win effects to play from a game's death and win handling
 * @param messages     BlockcraftAPI's own messages: chat tag texts and other strings shared by every plugin
 * @param servers      the live server list of the whole network
 * @param server       this server as the proxy knows it
 * @param groups       availability and population of every server group, for game cards and status signs
 * @param lobbyGroup   the group of the lobbies players fall back to
 * @param presence     what this server tells the proxy about itself
 * @param tags         the network addresses and the colour palette, resolved in every message of every Paper plugin
 * @param xp           network XP and levels; games award {@link XpReward}s
 * @param map          the map this server runs, with the files each plugin keeps inside it
 * @param buildMode    who is allowed to change the map right now
 * @param nickStats    the stats, rating and level others see for a nicked player instead of the account's own
 * @param achievements the achievement catalog; games hand it their round, level-ups reach it by event
 * @param elo          ranked 1vs1 ratings of the running season and the skill groups they fall into
 * @param ignores      whom each online player ignores; chat from those players does not reach them
 * @param audit        the staff audit log; whatever staff do to players or to the server is recorded here
 * @param calendar     the days the network celebrates, in its own time zone
 * @param roundConfig  what every game's round makes of a kill: nemesis lead, respect window, hacker words
 */
public record BlockcraftApi(
        Notifications notifications,
        NetworkOperations operations,
        PlayerTasks playerTasks,
        Database database,
        CoinsRepository coins,
        Unlocks unlocks,
        Shop shop,
        RoundRecapRepository recaps,
        Streaks streaks,
        PlayerSettings settings,
        ResourcePacks resourcePack,
        Profiles profiles,
        Effects effects,
        Messages messages,
        ServerRepository servers,
        ServerIdentity server,
        ServerGroups groups,
        String lobbyGroup,
        ServerPresence presence,
        TagResolver tags,
        Xp xp,
        MapBundle map,
        BuildMode buildMode,
        RealNames realNames,
        NickRanks nickRanks,
        NickStats nickStats,
        Achievements achievements,
        Elo elo,
        Afk afk,
        Ignores ignores,
        VanishedPlayers vanished,
        StaffAudit audit,
        NetworkCalendar calendar,
        RoundConfig roundConfig) {

    private static BlockcraftApi instance;

    public static BlockcraftApi get() {
        if (instance == null) {
            throw new IllegalStateException("BlockcraftAPI is not enabled");
        }
        return instance;
    }

    static void install(BlockcraftApi api) {
        instance = api;
    }

    static void uninstall() {
        instance = null;
    }

    /** Whether this server is one of the lobbies players fall back to, rather than a game. */
    public boolean isLobby() {
        return server.group().equals(lobbyGroup);
    }

    /** The name the player logged in with, whatever nick they wear; the one to write into name columns. */
    public String realName(Player player) {
        return realNames.of(player);
    }

    /** The LuckPerms group whose rank the player's nick shows, empty while they wear none. */
    public Optional<String> nickRank(Player player) {
        return nickRanks.of(player);
    }

    /** Whether the player is away from the keyboard; the tab list and the proxy never show it while they are vanished. */
    public boolean isAfk(Player player) {
        return afk.isAfk(player);
    }

    /** Whether the player is vanished, already true while the join of a vanished player is handled. */
    public boolean isVanished(Player player) {
        return vanished.isVanished(player);
    }
}
