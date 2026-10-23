package me.blockcraft.api.elo;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** One rung of the ranked ladder: the key names it in {@code elo.groups.<key>}, the rating is where it begins. */
public record SkillGroup(String key, int minRating) {

    /** The {@code elo.groups} config section, lowest rung first. */
    public static List<SkillGroup> ladder(Map<String, Object> section) {
        return section.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Number)
                .map(entry -> new SkillGroup(entry.getKey(), ((Number) entry.getValue()).intValue()))
                .sorted(Comparator.comparingInt(SkillGroup::minRating))
                .toList();
    }
}
