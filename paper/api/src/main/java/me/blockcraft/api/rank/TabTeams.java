package me.blockcraft.api.rank;

import me.blockcraft.api.nick.NickChangeEvent;
import me.blockcraft.api.scoreboard.Boards;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sorts the tab list by LuckPerms group weight and shows the group's prefix in front of each name, on every
 * Paper server. The heaviest inherited group counts, not the primary one: /addpremium and the shop only add an
 * inheritance node. Every player has their own scoreboard for the sidebar, so the teams are written to all of
 * them; entries are remembered per UUID because a nick changes the name the tab list is keyed by.
 */
public final class TabTeams implements Listener {

    private static final int MAX_WEIGHT = 9999;
    private static final int MAX_TEAM_NAME = 16;

    private final LuckPerms luckPerms;
    private final Messages messages;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    private record Entry(String team, Component prefix, String name) {
    }

    public TabTeams(LuckPerms luckPerms, Messages messages) {
        this.luckPerms = luckPerms;
        this.messages = messages;
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
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer());
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
        Group group = user.getInheritedGroups(user.getQueryOptions()).stream()
                .max(Comparator.comparingInt(inherited -> inherited.getWeight().orElse(0)))
                .orElseGet(() -> luckPerms.getGroupManager().getGroup(user.getPrimaryGroup()));
        int weight = group == null ? 0 : group.getWeight().orElse(0);
        String groupName = group == null ? user.getPrimaryGroup() : group.getName();
        Component prefix = Ranks.prefix(player).orElseGet(() -> messages.get("tab.default-prefix"));
        return new Entry(teamName(weight, groupName), prefix, player.getName());
    }

    private static void apply(Scoreboard board, Entry entry) {
        var team = board.getTeam(entry.team());
        if (team == null) {
            team = board.registerNewTeam(entry.team());
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
        }
        team.prefix(entry.prefix());
        team.addEntry(entry.name());
    }

    private static String teamName(int weight, String group) {
        String name = String.format("%04d%s", MAX_WEIGHT - Math.clamp(weight, 0, MAX_WEIGHT), group);
        return name.length() > MAX_TEAM_NAME ? name.substring(0, MAX_TEAM_NAME) : name;
    }
}
