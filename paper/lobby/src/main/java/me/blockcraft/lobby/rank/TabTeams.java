package me.blockcraft.lobby.rank;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Optional;
import java.util.OptionalInt;

/** Sorts the tab list by LuckPerms group weight and shows the group's prefix in front of each name. */
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
        String group = user.getPrimaryGroup();
        int weight = Optional.ofNullable(luckPerms.getGroupManager().getGroup(group))
                .map(Group::getWeight).filter(OptionalInt::isPresent).map(OptionalInt::getAsInt).orElse(0);
        Component prefix = Optional.ofNullable(user.getCachedData().getMetaData().getPrefix())
                .<Component>map(LegacyComponentSerializer.legacyAmpersand()::deserialize)
                .orElseGet(() -> messages.get("tab.default-prefix"));

        Team current = scoreboard.getEntryTeam(player.getName());
        if (current != null) {
            current.removeEntry(player.getName());
        }
        team(teamName(weight, group), prefix).addEntry(player.getName());
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
