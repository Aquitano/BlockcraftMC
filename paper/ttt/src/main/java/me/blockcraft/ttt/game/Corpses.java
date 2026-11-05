package me.blockcraft.ttt.game;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The dead stay where they fell, as a figure wearing their skin. Each finder learns the victim's role privately.
 * The DNA scanner reports foreign DNA and approximate time since death. A traitor may burn a corpse to prevent
 * further inspection, or take its face for a mask, which leaves the figure faceless.
 */
public final class Corpses {

    /** Who lies there, what they wore and when they died, in server ticks. */
    public record Remains(UUID player, String name, Role role, PlayerProfile profile, Location location, int diedAt) {
    }

    private static final class Corpse {
        private final Remains remains;
        private final boolean foreignDna;
        private final Set<UUID> identifiedBy = new HashSet<>();

        private Corpse(Remains remains, boolean foreignDna) {
            this.remains = remains;
            this.foreignDna = foreignDna;
            identifiedBy.add(remains.player());
        }
    }

    private final Messages messages;
    private final Map<UUID, Corpse> corpses = new HashMap<>();
    private final Map<UUID, Set<UUID>> burned = new HashMap<>();

    public Corpses(Messages messages) {
        this.messages = messages;
    }

    public void spawn(Player victim, Role role, Optional<Player> killer) {
        var location = victim.getLocation();
        location.setPitch(0);
        var remains = new Remains(victim.getUniqueId(), victim.getName(), role, victim.getPlayerProfile(), location.clone(),
                Bukkit.getCurrentTick());
        var corpse = new Corpse(remains, killer.filter(other -> !other.equals(victim)).isPresent());
        var pose = Mannequin.validPoses().contains(Pose.SLEEPING) ? Pose.SLEEPING : Pose.SWIMMING;
        var figure = location.getWorld().spawn(location, Mannequin.class, mannequin -> {
            mannequin.setProfile(ResolvableProfile.resolvableProfile(victim.getPlayerProfile()));
            mannequin.setDescription(null);
            mannequin.setPose(pose, true);
            mannequin.setImmovable(true);
            mannequin.setInvulnerable(true);
            mannequin.setAI(false);
            mannequin.setSilent(true);
            mannequin.setPersistent(false);
            mannequin.setRemoveWhenFarAway(false);
            mannequin.customName(messages.get("corpse.unknown"));
            mannequin.setCustomNameVisible(true);
        });
        corpses.put(figure.getUniqueId(), corpse);
    }

    public boolean isCorpse(Entity entity) {
        return corpses.containsKey(entity.getUniqueId());
    }

    public Optional<Remains> remains(Entity entity) {
        return Optional.ofNullable(corpses.get(entity.getUniqueId())).map(corpse -> corpse.remains);
    }

    public Set<UUID> deadPlayers() {
        return Stream.concat(burned.keySet().stream(), corpses.values().stream()
                .map(corpse -> corpse.remains.player())).collect(Collectors.toSet());
    }

    public Set<UUID> unidentified(UUID viewer) {
        return Stream.concat(burned.entrySet().stream().filter(entry -> !entry.getValue().contains(viewer)).map(Map.Entry::getKey),
                corpses.values().stream().filter(corpse -> !corpse.identifiedBy.contains(viewer))
                        .map(corpse -> corpse.remains.player())).collect(Collectors.toSet());
    }

    public Optional<UUID> inspect(Player finder, Entity entity, boolean scanner) {
        var corpse = corpses.get(entity.getUniqueId());
        if (corpse == null) {
            return Optional.empty();
        }
        boolean firstFind = corpse.identifiedBy.add(finder.getUniqueId());
        var who = TagResolver.resolver(Placeholder.unparsed("player", corpse.remains.name()),
                Placeholder.component("role", corpse.remains.role().displayName(messages)));
        finder.sendMessage(messages.get("corpse.known", who));
        if (scanner) {
            finder.sendMessage(messages.get(corpse.foreignDna ? "corpse.foreign-dna" : "corpse.no-killer", who));
            int seconds = Math.max(0, Bukkit.getCurrentTick() - corpse.remains.diedAt()) / 20;
            finder.sendMessage(messages.get("corpse.age", Formatter.number("min", seconds / 15 * 15),
                    Formatter.number("max", seconds / 15 * 15 + 15)));
            Sounds.NOTE_PLING.play(finder);
        } else if (firstFind) {
            Sounds.NOTIFY.play(finder);
        }
        return firstFind ? Optional.of(corpse.remains.player()) : Optional.empty();
    }

    /** Burns the corpse to nothing; if nobody had identified it, nobody ever will. */
    public boolean burn(Entity entity) {
        var corpse = corpses.remove(entity.getUniqueId());
        if (corpse == null) {
            return false;
        }
        burned.put(corpse.remains.player(), Set.copyOf(corpse.identifiedBy));
        var at = entity.getLocation();
        at.getWorld().spawnParticle(Particle.FLAME, at, 40, 0.5, 0.2, 0.5, 0.02);
        at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 20, 0.4, 0.3, 0.4, 0.01);
        at.getWorld().playSound(at, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
        entity.remove();
        return true;
    }

    /** Takes the corpse's face for a mask and leaves the figure without one. */
    public Optional<Remains> takeFace(Entity entity) {
        var corpse = corpses.get(entity.getUniqueId());
        if (corpse == null || !(entity instanceof Mannequin figure)
                || figure.getProfile().properties().isEmpty()) {
            return Optional.empty();
        }
        figure.setProfile(Mannequin.defaultProfile());
        return Optional.of(corpse.remains);
    }

    /** The corpse is gone because its player lives again. */
    public void remove(Entity entity) {
        if (corpses.remove(entity.getUniqueId()) != null) {
            entity.remove();
        }
    }

    public void clear() {
        corpses.keySet().forEach(id -> {
            var entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        });
        corpses.clear();
        burned.clear();
    }
}
