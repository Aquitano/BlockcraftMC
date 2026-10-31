package me.blockcraft.bedwars.map;

import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.calendar.NetworkCalendar.Occasion;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Which server a map was built for: {@code era: og} in its map.yml marks a map of the old one. */
public enum Era {
    CURRENT, OG;

    /** The vote offers the old maps while the network celebrates {@link Occasion#OG}. */
    public static Era of(NetworkCalendar calendar) {
        return calendar.is(Occasion.OG) ? OG : CURRENT;
    }

    public static Optional<Era> byKey(String key) {
        return Arrays.stream(values()).filter(era -> era.key().equalsIgnoreCase(key)).findFirst();
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The maps of this era, or the others when it has none, so that a vote never runs out of maps. */
    public List<ArenaMap> pick(List<ArenaMap> maps) {
        var own = maps.stream().filter(map -> map.era() == this).toList();
        return own.isEmpty() ? maps : own;
    }
}
