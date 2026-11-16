package me.blockcraft.story.campaign;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Where one player stands in a mission: the campaign they left open, in the first chapter that has one, and the
 * campaign they last finished in each chapter they have finished.
 */
public record Progress(Optional<Open> open, Map<String, Campaign> finished) {

    public record Open(String chapter, Campaign campaign) { }

    /** Asks the database, so not on the main thread. {@code byChapter} is in the chapters' order. */
    public static Progress of(UUID player, Map<String, Campaigns> byChapter) {
        Optional<Open> open = Optional.empty();
        var finished = new LinkedHashMap<String, Campaign>();
        for (var chapter : byChapter.entrySet()) {
            if (open.isEmpty()) open = chapter.getValue().unfinished(player).map(campaign -> new Open(chapter.getKey(), campaign));
            chapter.getValue().finished(player).ifPresent(campaign -> finished.put(chapter.getKey(), campaign));
        }
        return new Progress(open, finished);
    }
}
