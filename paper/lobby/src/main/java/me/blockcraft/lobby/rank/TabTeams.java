package me.blockcraft.lobby.rank;

import me.blockcraft.api.rank.Ranks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Comparator;

/**
 * Sorts the tab list by LuckPerms group weight and shows the group's prefix in front of each name. The heaviest
 * inherited group counts, not the primary one: /addpremium and the shop only add an inheritance node.
 */
public final class TabTeams {

    private static final int MAX_WEIGHT = 9999;
    private static final int MAX_TEAM_NAME = 16;

    private final LuckPerms luckPerms;
    private final Messages messages;
    private final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

    public TabTeams(LuckPerms luckPerms, Messages messages) {
        this.luckPerms = luckPerms;
        this.messages = messages;
    }

    public void update(Player player) {
        var user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        Group group = user.getInheritedGroups(user.getQueryOptions()).stream()
                .max(Comparator.comparingInt(inherited -> inherited.getWeight().orElse(0)))
                .orElseGet(() -> luckPerms.getGroupManager().getGroup(user.getPrimaryGroup()));
        int weight = group == null ? 0 : group.getWeight().orElse(0);
        String groupName = group == null ? user.getPrimaryGroup() : group.getName();
        Component prefix = Ranks.prefix(player).orElseGet(() -> messages.get("tab.default-prefix"));

        Team current = scoreboard.getEntryTeam(player.getName());
        if (current != null) {
            current.removeEntry(player.getName());
        }
        team(teamName(weight, groupName), prefix).addEntry(player.getName());
        player.setScoreboard(scoreboard);
    }

    private Team team(String name, Component prefix) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
            team.setAllowFriendlyFire(false);
            team.setCanSeeFriendlyInvisibles(true);
        }
        team.prefix(prefix);
        return team;
    }

    private static String teamName(int weight, String group) {
        String name = String.format("%04d%s", MAX_WEIGHT - Math.clamp(weight, 0, MAX_WEIGHT), group);
        return name.length() > MAX_TEAM_NAME ? name.substring(0, MAX_TEAM_NAME) : name;
    }
}
