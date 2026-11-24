package me.blockcraft.system.welcome;

import java.util.List;

/** Whom a newcomer is told greeted them: the first greeters by name, and how many more there were. */
record Greeters(List<String> named, int others) {

    /**
     * Names up to {@code listed} greeters and counts the rest. A single one left over is named as well, because
     * German has no gender-neutral way to say "one more".
     */
    static Greeters of(List<String> greeters, int listed) {
        int named = greeters.size() <= listed + 1 ? greeters.size() : listed;
        return new Greeters(List.copyOf(greeters.subList(0, named)), greeters.size() - named);
    }
}
