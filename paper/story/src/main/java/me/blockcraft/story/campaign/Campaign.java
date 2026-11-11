package me.blockcraft.story.campaign;

import me.blockcraft.story.state.Facts;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One party's play of a mission as it was last saved: its facts, {@code stage} among them, and its ending once it has
 * one. Each save raises the revision by one; revision 0 has never been saved.
 */
public record Campaign(UUID id, int revision, Map<String, String> facts, Optional<String> ending) {

    public Campaign {
        facts = Map.copyOf(facts);
    }

    public static Campaign fresh() {
        return new Campaign(UUID.randomUUID(), 0, Map.of(), Optional.empty());
    }

    public String stage() {
        return facts.getOrDefault(Facts.STAGE, "");
    }

    Campaign next(Map<String, String> facts, Optional<String> ending) {
        return new Campaign(id, revision + 1, facts, ending);
    }
}
