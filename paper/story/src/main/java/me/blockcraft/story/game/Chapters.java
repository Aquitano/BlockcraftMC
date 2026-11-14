package me.blockcraft.story.game;

import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A mission's chapters in order, from {@code chapters} in config.yml. Each is a run of the stages, from its
 * {@code from} up to the next chapter's, with its own ending and its own campaign, and plays in its {@code world}, or
 * the server's own. A chapter opens for whoever has finished the one before it, and {@code carries} names the facts it
 * takes over from that chapter's ending. A mission without {@code chapters} is one chapter, all of it.
 */
public record Chapters(List<Chapter> all) {

    /** The mission's name keys a campaign in the database, and a chapter's key is that name with its own. */
    private static final int LONGEST_KEY = 32;

    public record Chapter(String name, String campaignKey, List<String> stages, List<String> carries, Optional<String> world) {

        public Chapter {
            stages = List.copyOf(stages);
            carries = List.copyOf(carries);
        }
    }

    public static Chapters load(String mission, List<String> stages, @Nullable ConfigurationSection section) {
        if (stages.isEmpty()) throw new IllegalArgumentException("The mission has no stages");
        if (section == null) return new Chapters(List.of(new Chapter(mission, mission, stages, List.of(), Optional.empty())));
        var names = List.copyOf(section.getKeys(false));
        var starts = names.stream().map(name -> {
            var from = section.getString(name + ".from", "");
            if (!stages.contains(from)) throw new IllegalArgumentException("Chapter " + name + " starts at " + from + ", which is no stage");
            return stages.indexOf(from);
        }).toList();
        if (names.isEmpty() || starts.getFirst() != 0) throw new IllegalArgumentException("The first chapter has to start at the first stage, " + stages.getFirst());
        var chapters = new ArrayList<Chapter>();
        for (int index = 0; index < names.size(); index++) {
            int end = index + 1 < names.size() ? starts.get(index + 1) : stages.size();
            if (end <= starts.get(index)) throw new IllegalArgumentException("Chapter " + names.get(index) + " has to start before the next one");
            var key = mission + "." + names.get(index);
            if (key.length() > LONGEST_KEY) throw new IllegalArgumentException(key + " is longer than " + LONGEST_KEY + " characters");
            chapters.add(new Chapter(names.get(index), key, stages.subList(starts.get(index), end), section.getStringList(names.get(index) + ".carries"),
                    Optional.ofNullable(section.getString(names.get(index) + ".world"))));
        }
        return new Chapters(chapters);
    }

    public Optional<Chapter> named(String name) {
        return all.stream().filter(chapter -> chapter.name().equals(name)).findFirst();
    }

    public Optional<Chapter> of(String stage) {
        return all.stream().filter(chapter -> chapter.stages().contains(stage)).findFirst();
    }

    public Optional<Chapter> before(Chapter chapter) {
        int index = all.indexOf(chapter);
        return index > 0 ? Optional.of(all.get(index - 1)) : Optional.empty();
    }

    public Optional<Chapter> after(Chapter chapter) {
        int index = all.indexOf(chapter);
        return index >= 0 && index + 1 < all.size() ? Optional.of(all.get(index + 1)) : Optional.empty();
    }

    /**
     * The chapter a party plays next, given the chapters each member has finished: the earliest one any of them has
     * still to finish, so nobody skips ahead of a member. A party that has finished them all starts over.
     */
    public Chapter next(Collection<Set<String>> finishedByMember) {
        int earliest = finishedByMember.stream().mapToInt(this::reached).min().orElse(0);
        return all.get(earliest < all.size() ? earliest : 0);
    }

    private int reached(Set<String> finished) {
        int index = 0;
        while (index < all.size() && finished.contains(all.get(index).name())) index++;
        return index;
    }
}
