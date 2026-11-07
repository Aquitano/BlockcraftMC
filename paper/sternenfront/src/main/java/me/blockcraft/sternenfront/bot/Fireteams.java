package me.blockcraft.sternenfront.bot;

import me.blockcraft.sternenfront.game.CombatClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/** How a faction's bots split into fireteams, and who stays behind at a post the team has taken. */
final class Fireteams {

    record Member(int number, CombatClass role) { }

    private static final List<CombatClass> LEADS_FIRST = List.of(CombatClass.SOLDIER, CombatClass.SCOUT,
            CombatClass.HEAVY, CombatClass.MEDIC);
    private static final int SMALLEST_TO_SPARE_A_GUARD = 3;

    private Fireteams() { }

    /**
     * Teams of at most {@code size} whose sizes differ by one at most. The roles are dealt across them, so each team
     * opens with its best leader and gets its share of heavies and medics.
     */
    static List<List<Integer>> assign(List<Member> members, int size) {
        if (members.isEmpty()) {
            return List.of();
        }
        int count = (members.size() + size - 1) / size;
        var dealt = members.stream().sorted(Comparator.comparingInt((Member member) -> LEADS_FIRST.indexOf(member.role()))
                .thenComparingInt(Member::number)).toList();
        var teams = IntStream.range(0, count).mapToObj(ignored -> new ArrayList<Integer>()).toList();
        for (int index = 0; index < dealt.size(); index++) {
            teams.get(index % count).add(dealt.get(index).number());
        }
        return teams.stream().map(List::copyOf).toList();
    }

    /** Whoever holds a post the team just took while the rest push on: its heavy, else its last member. */
    static OptionalInt guard(List<Member> alive) {
        if (alive.size() < SMALLEST_TO_SPARE_A_GUARD) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(alive.stream().filter(member -> member.role() == CombatClass.HEAVY).findFirst()
                .orElse(alive.getLast()).number());
    }
}
