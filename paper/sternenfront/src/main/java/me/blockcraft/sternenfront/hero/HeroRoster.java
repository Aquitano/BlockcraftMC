package me.blockcraft.sternenfront.hero;

import me.blockcraft.sternenfront.game.Faction;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who fights as which hero right now. A faction fields at most {@code perFaction} bought heroes at once and each of
 * them only once; the champion of the relic is a hero too but takes no faction's slot.
 */
public final class HeroRoster {

    private final int perFaction;
    private final Map<UUID, Hero> heroes = new HashMap<>();
    private final Set<Faction> extraSlot = EnumSet.noneOf(Faction.class);

    public HeroRoster(int perFaction) {
        this.perFaction = perFaction;
    }

    public Optional<Hero> of(UUID player) {
        return Optional.ofNullable(heroes.get(player));
    }

    /** Whether {@code faction} may field {@code hero} now. */
    public boolean open(Faction faction, Hero hero) {
        return hero.faction().filter(faction::equals).isPresent() && !heroes.containsValue(hero)
                && heroes.values().stream().filter(each -> each.faction().filter(faction::equals).isPresent()).count()
                < perFaction + (extraSlot.contains(faction) ? 1 : 0);
    }

    /** {@code faction} may field one more hero at once. */
    public void grantSlot(Faction faction) {
        extraSlot.add(faction);
    }

    /** Makes the player {@code hero}; false if the slot is taken or the player is a hero already. */
    public boolean enlist(UUID player, Faction faction, Hero hero) {
        if (heroes.containsKey(player) || hero != Hero.CHAMPION && !open(faction, hero)) {
            return false;
        }
        heroes.put(player, hero);
        return true;
    }

    public Optional<Hero> discharge(UUID player) {
        return Optional.ofNullable(heroes.remove(player));
    }

    public Map<UUID, Hero> all() {
        return Map.copyOf(heroes);
    }

    public void clear() {
        heroes.clear();
        extraSlot.clear();
    }
}
