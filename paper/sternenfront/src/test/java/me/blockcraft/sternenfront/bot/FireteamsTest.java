package me.blockcraft.sternenfront.bot;

import me.blockcraft.sternenfront.bot.Fireteams.Member;
import me.blockcraft.sternenfront.game.CombatClass;
import org.junit.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;

public class FireteamsTest {

    @Test
    public void teamsAreBalancedLedBySoldiersAndShareTheHeaviesAndMedics() {
        var members = List.of(new Member(1, CombatClass.SOLDIER), new Member(2, CombatClass.HEAVY),
                new Member(3, CombatClass.MEDIC), new Member(4, CombatClass.SCOUT), new Member(5, CombatClass.SOLDIER),
                new Member(6, CombatClass.HEAVY), new Member(7, CombatClass.MEDIC), new Member(8, CombatClass.SCOUT));
        assertEquals(List.of(List.of(1, 4, 2, 3), List.of(5, 8, 6, 7)), Fireteams.assign(members, 4));
        assertEquals(List.of(List.of(1, 4, 6), List.of(5, 2, 3)), Fireteams.assign(members.subList(0, 6), 4));
        assertEquals(List.of(List.of(1, 2, 3)), Fireteams.assign(members.subList(0, 3), 4));
    }

    @Test
    public void aTakenPostKeepsTheHeavyElseARiflemanButNeverTheLeaderOrMedic() {
        var team = List.of(new Member(1, CombatClass.SOLDIER), new Member(4, CombatClass.SCOUT),
                new Member(2, CombatClass.HEAVY), new Member(3, CombatClass.MEDIC));
        assertEquals(OptionalInt.of(2), Fireteams.guard(team, 1));
        assertEquals(OptionalInt.of(4), Fireteams.guard(List.of(team.get(0), team.get(1), team.get(3)), 1));
        assertEquals(OptionalInt.empty(), Fireteams.guard(List.of(team.get(0), new Member(5, CombatClass.MEDIC), team.get(3)), 1));
        assertEquals(OptionalInt.empty(), Fireteams.guard(team.subList(0, 2), 1));
    }
}
