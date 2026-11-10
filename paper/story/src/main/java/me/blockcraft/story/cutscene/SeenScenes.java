package me.blockcraft.story.cutscene;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.db.Database;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which cutscenes a player has watched to the end, from {@code bc_story_seen_scenes}, as {@code <mission>.<scene>}.
 * Loaded when a player joins; a player whose rows have not arrived yet counts as having seen nothing.
 */
public final class SeenScenes {

    private final Plugin plugin;
    private final Database db;
    private final String mission;
    private final Map<UUID, Set<String>> seen = new HashMap<>();

    public SeenScenes(Plugin plugin, Database db, String mission) {
        this.plugin = plugin;
        this.db = db;
        this.mission = mission;
    }

    public void load(Player player) {
        var id = player.getUniqueId();
        Tasks.async(plugin, () -> db.queryList("SELECT scene FROM bc_story_seen_scenes WHERE player_uuid = ?",
                row -> row.getString("scene"), id.toString()), scenes -> seen.computeIfAbsent(id, ignored -> new HashSet<>()).addAll(scenes));
    }

    public boolean seen(Player player, String scene) {
        return seen.getOrDefault(player.getUniqueId(), Set.of()).contains(key(scene));
    }

    public void record(Player player, String scene) {
        var id = player.getUniqueId();
        var key = key(scene);
        if (!seen.computeIfAbsent(id, ignored -> new HashSet<>()).add(key)) return;
        Tasks.async(plugin, () -> db.update("INSERT IGNORE INTO bc_story_seen_scenes (player_uuid, scene) VALUES (?, ?)", id.toString(), key));
    }

    private String key(String scene) {
        return mission + "." + scene;
    }
}
