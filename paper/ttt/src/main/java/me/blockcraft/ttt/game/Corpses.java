package me.blockcraft.ttt.game;

import me.blockcraft.api.BlockcraftApi;
import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
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
 * The dead stay where they fell, as a figure wearing their skin. Whoever finds one identifies it for everyone,
 * which reveals the role; the DNA scanner also tells its holder who the killer was. A traitor may burn a corpse, so
 * that nobody ever identifies it, or take its face for a mask, which leaves the figure faceless.
 */
public final class Corpses {

    /** Who lies there, what they wore and when they died, in server ticks. */
    public record Remains(UUID player, String name, Role role, PlayerProfile profile, Location location, int diedAt) {
    }

    private static final class Corpse {
        private final Remains remains;
        private final Optional<String> killer;
        private boolean identified;

        private Corpse(Remains remains, Optional<String> killer) {
            this.remains = remains;
            this.killer = killer;
        }
    }

    private final Messages messages;
    private final Map<UUID, Corpse> corpses = new HashMap<>();
    private final Set<UUID> burned = new HashSet<>();

    public Corpses(Messages messages) {
        this.messages = messages;
    }

    public void spawn(Player victim, Role role, Optional<Player> killer) {
        var location = victim.getLocation();
        location.setPitch(0);
        var remains = new Remains(victim.getUniqueId(), victim.getName(), role, victim.getPlayerProfile(), location.clone(),
                Bukkit.getCurrentTick());
        var corpse = new Corpse(remains, killer.filter(other -> !other.equals(victim)).map(Player::getName));
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

    /** The dead nobody has identified yet, whom everyone else still counts among the living; burned ones stay so. */
    public Set<UUID> unidentified() {
        return Stream.concat(burned.stream(), corpses.values().stream().filter(corpse -> !corpse.identified)
                .map(corpse -> corpse.remains.player())).collect(Collectors.toSet());
    }

    /**
     * Identifies the corpse on first touch, and returns whose it was then; with the DNA scanner in hand, the finder
     * also learns the killer.
     */
    public Optional<UUID> inspect(Player finder, Entity entity, boolean scanner) {
        var corpse = corpses.get(entity.getUniqueId());
        if (corpse == null) {
            return Optional.empty();
        }
        boolean firstFind = !corpse.identified;
        var who = TagResolver.resolver(Placeholder.unparsed("player", corpse.remains.name()),
                Placeholder.component("role", corpse.remains.role().displayName(messages)));
        if (firstFind) {
            corpse.identified = true;
            entity.customName(messages.get("corpse.identified", who));
            Bukkit.broadcast(messages.get("corpse.found", who, Placeholder.unparsed("finder", finder.getName())));
            Bukkit.getOnlinePlayers().forEach(Sounds.NOTIFY::play);
        } else if (!scanner) {
            BlockcraftApi.get().notifications().feedback(finder, messages.get("corpse.known", who));
        }
        if (scanner) {
            finder.sendMessage(corpse.killer
                    .map(killer -> messages.get("corpse.killer", who, Placeholder.unparsed("killer", killer)))
                    .orElseGet(() -> messages.get("corpse.no-killer", who)));
            Sounds.NOTE_PLING.play(finder);
        }
        return firstFind ? Optional.of(corpse.remains.player()) : Optional.empty();
    }

    /** Burns the corpse to nothing; if nobody had identified it, nobody ever will. */
    public boolean burn(Entity entity) {
        var corpse = corpses.remove(entity.getUniqueId());
        if (corpse == null) {
            return false;
        }
        if (!corpse.identified) {
            burned.add(corpse.remains.player());
        }
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
