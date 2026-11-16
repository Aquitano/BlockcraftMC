package me.blockcraft.story.game;

import me.blockcraft.common.message.Messages;
import me.blockcraft.story.cutscene.Cutscenes;
import me.blockcraft.story.dialogue.DialogueHud;
import me.blockcraft.story.quest.StoryNpcs;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A chapter's world with what it holds for the party: where the members land and where they rally, and the NPCs
 * standing there, from {@code positions}: {@code landing}, {@code rally} and {@code npcs}, each NPC standing {@code at} a
 * spot and wearing the BetterModel {@code model} it names.
 */
final class Setting implements AutoCloseable {

    private final Plugin plugin;
    private final World world;
    private final List<Location> landing;
    private final Location rally;
    private final Map<String, String> models = new LinkedHashMap<>();
    private final List<NpcPresence> presences = new ArrayList<>();
    private final List<Mannequin> spawned = new ArrayList<>();
    private final Set<Long> tickets = new HashSet<>();

    private Setting(Plugin plugin, World world, List<Location> landing, Location rally) {
        this.plugin = plugin;
        this.world = world;
        this.landing = landing;
        this.rally = rally;
    }

    /** Reads {@code positions} without a world, to refuse a mission whose positions do not parse before it is played. */
    static void check(ConfigurationSection positions) {
        Spots.all(null, positions, "landing");
        Spots.at(null, positions, "rally");
        var npcs = positions.getConfigurationSection("npcs");
        if (npcs != null) npcs.getKeys(false).forEach(id -> Spots.at(null, npcs.getConfigurationSection(id), "at"));
    }

    /** The NPCs are named by {@code npc.<id>.name} and {@code npc.<id>.role} in messages.yml. */
    static Setting in(Plugin plugin, World world, ConfigurationSection positions, StoryNpcs npcs, Messages messages,
                      Supplier<List<Player>> members, DialogueHud dialogue) {
        var setting = new Setting(plugin, world, Spots.all(world, positions, "landing"), Spots.at(world, positions, "rally"));
        var section = positions.getConfigurationSection("npcs");
        if (section != null) {
            for (var id : section.getKeys(false)) {
                var npc = section.getConfigurationSection(id);
                var post = Spots.at(world, npc, "at");
                setting.models.put(id, npc.getString("model", ""));
                setting.presences.add(new NpcPresence(id, setting.spawn(id, post, npcs, messages), post, members, dialogue));
            }
        }
        return setting;
    }

    private Mannequin spawn(String id, Location at, StoryNpcs npcs, Messages messages) {
        world.addPluginChunkTicket(at.getBlockX() >> 4, at.getBlockZ() >> 4, plugin);
        tickets.add(Chunk.getChunkKey(at.getBlockX() >> 4, at.getBlockZ() >> 4));
        var npc = world.spawn(at, Mannequin.class, entity -> {
            entity.setPersistent(false);
            entity.setImmovable(true);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.customName(messages.get("npc." + id + ".name"));
            entity.setCustomNameVisible(true);
            entity.setDescription(messages.get("npc." + id + ".role"));
        });
        npcs.put(id, npc);
        spawned.add(npc);
        return npc;
    }

    World world() {
        return world;
    }

    Location rally() {
        return rally;
    }

    /** Where the {@code index}th member lands. */
    Location landing(int index) {
        return landing.isEmpty() ? rally : landing.get(index % landing.size());
    }

    /** The NPCs get their bodies once BetterModel runs, which loads after the plugins. */
    void dress() {
        presences.forEach(npc -> npc.dress(models.getOrDefault(npc.id(), "")));
    }

    void hold(boolean held) {
        presences.forEach(npc -> npc.hold(held));
    }

    void tick() {
        presences.forEach(NpcPresence::tick);
    }

    Map<String, Cutscenes.Npc> cast() {
        var cast = new HashMap<String, Cutscenes.Npc>();
        spawned.stream().filter(Mannequin::isValid).forEach(npc -> presences.stream().filter(presence -> presence.is(npc))
                .findFirst().ifPresent(presence -> cast.put(presence.id(), new Cutscenes.Npc(npc, models.getOrDefault(presence.id(), "")))));
        return cast;
    }

    @Override
    public void close() {
        spawned.forEach(Mannequin::remove);
        spawned.clear();
        presences.clear();
        tickets.forEach(key -> world.removePluginChunkTicket((int) (long) key, (int) (key >> 32), plugin));
        tickets.clear();
    }
}
