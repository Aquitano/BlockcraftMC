package me.blockcraft.ttt.game;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The dead stay where they fell, as a figure wearing their skin. Whoever finds one identifies it for everyone,
 * which reveals the role; the DNA scanner also tells its holder who the killer was.
 */
public final class Corpses {

    private static final class Corpse {
        private final String name;
        private final Role role;
        private final Optional<String> killer;
        private boolean identified;

        private Corpse(String name, Role role, Optional<String> killer) {
            this.name = name;
            this.role = role;
            this.killer = killer;
        }
    }

    private final Messages messages;
    private final Map<UUID, Corpse> corpses = new HashMap<>();

    public Corpses(Messages messages) {
        this.messages = messages;
    }

    public void spawn(Player victim, Role role, Optional<Player> killer) {
        var corpse = new Corpse(victim.getName(), role, killer.filter(other -> !other.equals(victim)).map(Player::getName));
        var location = victim.getLocation();
        location.setPitch(0);
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

    /** Identifies the corpse on first touch; with the DNA scanner in hand, the finder also learns the killer. */
    public void inspect(Player finder, Entity entity, boolean scanner) {
        var corpse = corpses.get(entity.getUniqueId());
        if (corpse == null) {
            return;
        }
        var who = TagResolver.resolver(Placeholder.unparsed("player", corpse.name),
                Placeholder.component("role", corpse.role.displayName(messages)));
        if (!corpse.identified) {
            corpse.identified = true;
            entity.customName(messages.get("corpse.identified", who));
            Bukkit.broadcast(messages.get("corpse.found", who, Placeholder.unparsed("finder", finder.getName())));
            Bukkit.getOnlinePlayers().forEach(Sounds.NOTIFY::play);
        } else if (!scanner) {
            finder.sendMessage(messages.get("corpse.known", who));
        }
        if (scanner) {
            finder.sendMessage(corpse.killer
                    .map(killer -> messages.get("corpse.killer", who, Placeholder.unparsed("killer", killer)))
                    .orElseGet(() -> messages.get("corpse.no-killer", who)));
            Sounds.NOTE_PLING.play(finder);
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
    }
}
