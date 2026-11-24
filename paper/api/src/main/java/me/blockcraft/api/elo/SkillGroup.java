package me.blockcraft.api.elo;

import me.blockcraft.common.config.StrictConfig;

import java.util.Comparator;
import java.util.List;

/** One rung of the ranked ladder: the key names it in {@code elo.groups.<key>}, the rating is where it begins. */
public record SkillGroup(String key, int minRating) {

    /** The {@code elo.groups} config section, lowest rung first. */
    public static List<SkillGroup> ladder(StrictConfig section) {
        return section.keys().stream()
                .map(key -> new SkillGroup(key, section.integer(key)))
                .sorted(Comparator.comparingInt(SkillGroup::minRating))
                .toList();
    }
}
