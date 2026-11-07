package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.game.Faction;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Each faction's fireteam colours from {@code appearance.unit-colours}, first fireteam first. */
record UnitColours(Map<Faction, List<Integer>> colours) {

    private static final List<Integer> UNMARKED = List.of(0xFFFFFF);

    static UnitColours from(ConfigurationSection appearance) {
        var colours = new EnumMap<Faction, List<Integer>>(Faction.class);
        for (var faction : Faction.values()) {
            var parsed = appearance.getStringList("unit-colours." + faction.key()).stream()
                    .map(hex -> TextColor.fromHexString("#" + hex)).filter(Objects::nonNull).map(TextColor::value).toList();
            colours.put(faction, parsed.isEmpty() ? UNMARKED : parsed);
        }
        return new UnitColours(colours);
    }

    /** A side with more fireteams than colours starts over at its first colour. */
    int of(Faction faction, int fireteam) {
        var own = colours.get(faction);
        return own.get(Math.floorMod(fireteam, own.size()));
    }
}
