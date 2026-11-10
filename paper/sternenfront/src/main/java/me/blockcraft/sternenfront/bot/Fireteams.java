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

    /** A member who could lead, with the ground it has {@code left} to the objective. */
    record Candidate(int number, boolean wounded, double left) { }

    /** Who stands near a bot, counting itself among its {@code friends}. */
    record Odds(int friends, int enemies) {

        /** Two to one or worse: a lone guard gives way to a pair, a full team to nobody it cannot match. */
        boolean outnumbered() {
            return enemies >= Math.max(2, friends * 2);
        }
    }

    private static final List<CombatClass> LEADS_FIRST = List.of(CombatClass.SOLDIER, CombatClass.SCOUT,
            CombatClass.HEAVY, CombatClass.MEDIC);
    private static final List<CombatClass> GUARDS_FIRST = List.of(CombatClass.HEAVY, CombatClass.SOLDIER, CombatClass.SCOUT);
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

    /**
     * The team keeps its leader while it is fit; a new one is the fit member with least ground left, so nobody turns
     * back. A wounded leader hands over, as it leaves the push to heal, unless nobody fit is left to take over.
     */
    static int leader(List<Candidate> squad, int current) {
        var fit = squad.stream().filter(candidate -> !candidate.wounded()).toList();
        var eligible = fit.isEmpty() ? squad : fit;
        return eligible.stream().filter(candidate -> candidate.number() == current).findFirst()
                .or(() -> eligible.stream().min(Comparator.comparingDouble(Candidate::left))).orElseThrow().number();
    }

    /**
     * Whoever holds a post the team just took while the rest push on: its heavy, else a rifleman. The leader stays in
     * front and the medic with the wounded, so a team without anyone else to spare pushes on whole.
     */
    static OptionalInt guard(List<Member> alive, int leader) {
        if (alive.size() < SMALLEST_TO_SPARE_A_GUARD) {
            return OptionalInt.empty();
        }
        return alive.stream().filter(member -> member.number() != leader && GUARDS_FIRST.contains(member.role()))
                .min(Comparator.comparingInt(member -> GUARDS_FIRST.indexOf(member.role())))
                .map(member -> OptionalInt.of(member.number())).orElseGet(OptionalInt::empty);
    }
}
