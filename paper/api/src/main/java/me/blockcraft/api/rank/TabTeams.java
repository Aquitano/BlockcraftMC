package me.blockcraft.api.rank;

import me.blockcraft.api.afk.Afk;
import me.blockcraft.api.afk.AfkChangeEvent;
import me.blockcraft.api.nick.NickChangeEvent;
import me.blockcraft.api.scoreboard.Boards;
import me.blockcraft.api.vanish.VanishChangeEvent;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sorts the tab list by LuckPerms group weight and shows the group's prefix in front of each name, on every
 * Paper server. The heaviest inherited group counts, not the primary one: /addpremium and the shop only add an
 * inheritance node; a nicked player sorts and reads as the group their nick wears. Every player has their own
 * scoreboard for the sidebar, so the teams are written to all of them; entries are remembered per UUID because a
 * nick changes the name the tab list is keyed by. Vanished players get their own team per group, suffixed with a
 * marker: only staff who see through vanish still list them. Players away from the keyboard get a team of their own
 * with an AFK marker, unless they are vanished. A game that plays in teams colours its players' names, in the tab
 * list and above their heads, without taking the rank prefix away.
 */
public final class TabTeams implements Listener {

    private static final int MAX_WEIGHT = 9999;
    private static final int MAX_TEAM_NAME = 16;
    private static final char VISIBLE = '-';
    private static final char VANISHED = 'v';
    private static final char AFK = 'a';
    private static final char UNCOLORED = '-';
    private static final List<NamedTextColor> COLORS = NamedTextColor.NAMES.values().stream()
            .sorted(Comparator.comparingInt(NamedTextColor::value)).toList();

    private final LuckPerms luckPerms;
    private final Messages messages;
    private final NickRanks nickRanks;
    private final Afk afk;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, NamedTextColor> colors = new HashMap<>();

    private record Entry(String team, Component prefix, Component suffix, Optional<NamedTextColor> color, String name) {
    }

    public TabTeams(LuckPerms luckPerms, Messages messages, NickRanks nickRanks, Afk afk) {
        this.luckPerms = luckPerms;
        this.messages = messages;
        this.nickRanks = nickRanks;
        this.afk = afk;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        update(event.getPlayer());
    }

    @EventHandler
    public void onNickChange(NickChangeEvent event) {
        update(event.getPlayer());
    }

    @EventHandler
    public void onVanishChange(VanishChangeEvent event) {
        if (event.isVanished()) {
            vanished.add(event.getPlayer().getUniqueId());
        } else {
            vanished.remove(event.getPlayer().getUniqueId());
        }
        update(event.getPlayer());
    }

    @EventHandler
    public void onAfkChange(AfkChangeEvent event) {
        update(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        vanished.remove(event.getPlayer().getUniqueId());
        colors.remove(event.getPlayer().getUniqueId());
        remove(event.getPlayer());
    }

    /** The colour of the player's game team on their name, until {@link #clearColor} or they leave. */
    public void color(Player player, NamedTextColor color) {
        colors.put(player.getUniqueId(), color);
        update(player);
    }

    public void clearColor(Player player) {
        if (colors.remove(player.getUniqueId()) != null) {
            update(player);
        }
    }

    public void update(Player player) {
        remove(player);
        var entry = entryOf(player);
        entries.put(player.getUniqueId(), entry);
        Bukkit.getOnlinePlayers().forEach(viewer -> apply(Boards.of(viewer), entry));
        var own = Boards.of(player);
        entries.values().forEach(known -> apply(own, known));
    }

    public void remove(Player player) {
        var entry = entries.remove(player.getUniqueId());
        if (entry == null) {
            return;
        }
        for (var viewer : Bukkit.getOnlinePlayers()) {
            var team = Boards.of(viewer).getEntryTeam(entry.name());
            if (team != null) {
                team.removeEntry(entry.name());
            }
        }
    }

    private Entry entryOf(Player player) {
        var user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        Group group = nickRanks.of(player)
                .map(luckPerms.getGroupManager()::getGroup)
                .orElseGet(() -> user.getInheritedGroups(user.getQueryOptions()).stream()
                        .max(Comparator.comparingInt(inherited -> inherited.getWeight().orElse(0)))
                        .orElseGet(() -> luckPerms.getGroupManager().getGroup(user.getPrimaryGroup())));
        int weight = group == null ? 0 : group.getWeight().orElse(0);
        String groupName = group == null ? user.getPrimaryGroup() : group.getName();
        Component prefix = Ranks.prefix(player).orElseGet(() -> messages.get("tab.default-prefix"));
        boolean isVanished = vanished.contains(player.getUniqueId());
        boolean isAfk = !isVanished && afk.isAfk(player);
        Component suffix = isVanished ? messages.get("tab.vanished-suffix")
                : isAfk ? messages.get("tab.afk-suffix") : Component.empty();
        var color = Optional.ofNullable(colors.get(player.getUniqueId()));
        return new Entry(teamName(weight, groupName, color, isVanished ? VANISHED : isAfk ? AFK : VISIBLE), prefix, suffix,
                color, player.getName());
    }

    private static void apply(Scoreboard board, Entry entry) {
        var team = board.getTeam(entry.team());
        if (team == null) {
            team = board.registerNewTeam(entry.team());
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
        }
        team.prefix(entry.prefix());
        team.suffix(entry.suffix());
        entry.color().ifPresent(team::color);
        team.addEntry(entry.name());
    }

    /**
     * Weight, group, the game team's colour, then the marker, so a game's teams sort together within a rank and AFK
     * and vanished players right after their visible mates.
     */
    private static String teamName(int weight, String group, Optional<NamedTextColor> color, char marker) {
        String name = String.format("%04d%s", MAX_WEIGHT - Math.clamp(weight, 0, MAX_WEIGHT), group);
        int room = MAX_TEAM_NAME - 2;
        char colorMark = color.map(named -> Character.forDigit(COLORS.indexOf(named), Character.MAX_RADIX)).orElse(UNCOLORED);
        return (name.length() > room ? name.substring(0, room) : name) + colorMark + marker;
    }
}
