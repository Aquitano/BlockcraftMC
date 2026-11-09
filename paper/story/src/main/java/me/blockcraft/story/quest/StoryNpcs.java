package me.blockcraft.story.quest;

import org.betonquest.betonquest.api.identifier.NpcIdentifier;
import org.betonquest.betonquest.api.profile.OnlineProfile;
import org.betonquest.betonquest.api.profile.Profile;
import org.betonquest.betonquest.api.profile.ProfileProvider;
import org.betonquest.betonquest.api.QuestException;
import org.betonquest.betonquest.api.instruction.Argument;
import org.betonquest.betonquest.api.quest.npc.Npc;
import org.betonquest.betonquest.api.quest.npc.NpcInteractCatcher;
import org.betonquest.betonquest.api.quest.npc.NpcReverseIdentifier;
import org.betonquest.betonquest.api.quest.npc.NpcWrapper;
import org.betonquest.betonquest.api.service.npc.NpcRegistry;
import org.betonquest.betonquest.quest.objective.interact.Interaction;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * NPCs this plugin spawns itself, offered to BetonQuest as the NPC type {@code blockcraft <key>}, so that a quest
 * package binds conversations to them without Citizens, FancyNpcs or ZNPCsPlus.
 */
public final class StoryNpcs {

    static final String TYPE = "blockcraft";

    private final Plugin plugin;
    private final Map<String, Mannequin> spawned = new HashMap<>();

    public StoryNpcs(Plugin plugin) {
        this.plugin = plugin;
    }

    public void put(String key, Mannequin entity) {
        spawned.put(key, entity);
    }

    public Optional<Mannequin> get(String key) {
        return Optional.ofNullable(spawned.get(key)).filter(Mannequin::isValid);
    }

    private Optional<String> keyOf(Mannequin entity) {
        return spawned.entrySet().stream().filter(entry -> entry.getValue().equals(entity)).map(Map.Entry::getKey).findFirst();
    }

    record StoryNpc(Plugin plugin, String key, Mannequin entity) implements Npc<Mannequin> {

        @Override
        public Mannequin getOriginal() {
            return entity;
        }

        @Override
        public String getName() {
            return key;
        }

        @Override
        public String getFormattedName() {
            return entity.getName();
        }

        @Override
        public Optional<Location> getLocation() {
            return Optional.of(entity.getLocation());
        }

        @Override
        public Optional<Location> getEyeLocation() {
            return Optional.of(entity.getEyeLocation());
        }

        @Override
        public void teleport(Location location) {
            entity.teleport(location);
        }

        @Override
        public boolean isSpawned() {
            return entity.isValid();
        }

        @Override
        public void spawn(Location location) {
            entity.teleport(location);
        }

        @Override
        public void despawn() {
            entity.remove();
        }

        @Override
        public void show(OnlineProfile profile) {
            profile.getPlayer().showEntity(plugin, entity);
        }

        @Override
        public void hide(OnlineProfile profile) {
            profile.getPlayer().hideEntity(plugin, entity);
        }
    }

    record Lookup(StoryNpcs npcs, Argument<String> key) implements NpcWrapper<Mannequin> {

        @Override
        public Npc<Mannequin> getNpc(@Nullable Profile profile) throws QuestException {
            var name = key.getValue(profile);
            var entity = npcs.get(name).orElseThrow(() -> new QuestException("No story NPC " + name + " is spawned"));
            return new StoryNpc(npcs.plugin, name, entity);
        }

        @Override
        public Set<Npc<Mannequin>> getNpcs(@Nullable Profile profile) throws QuestException {
            return Set.of(getNpc(profile));
        }
    }

    final class Identifier implements NpcReverseIdentifier {

        private final Map<String, Set<NpcIdentifier>> byKey = new HashMap<>();

        @Override
        public Set<NpcIdentifier> getIdsFromNpc(Npc<?> npc, @Nullable OnlineProfile profile) {
            if (!(npc.getOriginal() instanceof Mannequin entity)) return Set.of();
            return keyOf(entity).map(key -> byKey.getOrDefault(key, Set.of())).orElse(Set.of());
        }

        @Override
        public void addID(NpcIdentifier npcId, String instruction) {
            var parts = instruction.trim().split("\\s+");
            if (parts.length == 2 && parts[0].equals(TYPE)) byKey.computeIfAbsent(parts[1], key -> new HashSet<>()).add(npcId);
        }

        @Override
        public void reset() {
            byKey.clear();
        }
    }

    final class Clicks extends NpcInteractCatcher<Mannequin> {

        Clicks(ProfileProvider profiles, NpcRegistry registry) {
            super(profiles, registry);
        }

        @EventHandler(ignoreCancelled = true)
        public void onInteract(PlayerInteractEntityEvent event) {
            if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Mannequin entity)) return;
            keyOf(entity).ifPresent(key -> {
                if (interactLogic(event.getPlayer(), new StoryNpc(plugin, key, entity), Interaction.RIGHT, false, false)) {
                    event.setCancelled(true);
                }
            });
        }
    }
}
