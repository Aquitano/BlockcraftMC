package me.blockcraft.api.rank;

import me.blockcraft.api.afk.Afk;
import me.blockcraft.api.afk.AfkChangeEvent;
import me.blockcraft.api.nick.NickChangeEvent;
import me.blockcraft.api.scoreboard.Boards;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.vanish.VanishChangeEvent;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
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
 * Paper server; a viewer who wears the pack sees the group's badge instead. The heaviest inherited group counts, not
 * the primary one: a /rank on time and the shop only add an inheritance node; a nicked player sorts and reads as the
 * group their nick wears. Every player has their own scoreboard for the sidebar, so the teams are written to all of
 * them; entries are remembered per UUID because a nick changes the name the tab list is keyed by. Vanished players
 * get their own team per group, suffixed with a marker: only staff who see through vanish still list them. Players
 * away from the keyboard get a team of their own with an AFK marker, unless they are vanished. A game that plays in
 * teams colours its players' names, in the tab list and above their heads, without taking the rank prefix away. A
 * rank change shows as soon as LuckPerms recalculates the player.
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

    private record Entry(String team, RankPrefix prefix, Component suffix, Optional<NamedTextColor> color, String name) {
    }

    public TabTeams(Plugin plugin, LuckPerms luckPerms, Messages messages, NickRanks nickRanks, Afk afk) {
        this.luckPerms = luckPerms;
        this.messages = messages;
        this.nickRanks = nickRanks;
        this.afk = afk;
        luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            if (plugin.isEnabled()) {
                Tasks.sync(plugin, () -> refresh(event.getUser().getUniqueId()));
            }
        });
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

    /** The proxy's word on the pack arrives after the join, and whether a viewer sees badges depends on it. */
    @EventHandler
    public void onResourcePack(PlayerResourcePackStatusEvent event) {
        entries.values().forEach(known -> show(event.getPlayer(), known));
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

    /** A rank change reaches a player who is already listed; one still logging in is placed by the join. */
    private void refresh(UUID uuid) {
        var player = Bukkit.getPlayer(uuid);
        if (player != null && entries.containsKey(uuid)) {
            update(player);
        }
    }

    public void update(Player player) {
        remove(player);
        var entry = entryOf(player);
        entries.put(player.getUniqueId(), entry);
        Bukkit.getOnlinePlayers().forEach(viewer -> show(viewer, entry));
        entries.values().forEach(known -> show(player, known));
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
        var prefix = Ranks.prefix(player).orElseGet(() -> {
            var plain = messages.get("tab.default-prefix");
            return new RankPrefix(plain, plain);
        });
        boolean isVanished = vanished.contains(player.getUniqueId());
        boolean isAfk = !isVanished && afk.isAfk(player);
        Component suffix = isVanished ? messages.get("tab.vanished-suffix")
                : isAfk ? messages.get("tab.afk-suffix") : Component.empty();
        var color = Optional.ofNullable(colors.get(player.getUniqueId()));
        return new Entry(teamName(weight, groupName, color, isVanished ? VANISHED : isAfk ? AFK : VISIBLE), prefix, suffix,
                color, player.getName());
    }

    private static void show(Player viewer, Entry entry) {
        apply(Boards.of(viewer), entry, entry.prefix().shownTo(viewer));
    }

    private static void apply(Scoreboard board, Entry entry, Component prefix) {
        var team = board.getTeam(entry.team());
        if (team == null) {
            team = board.registerNewTeam(entry.team());
            team.setCanSeeFriendlyInvisibles(true);
        }
        team.setAllowFriendlyFire(true);
        team.prefix(prefix);
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
