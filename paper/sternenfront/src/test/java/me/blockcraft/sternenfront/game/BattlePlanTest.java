package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.game.BattlePlan.Point;
import me.blockcraft.sternenfront.game.BattlePlan.Post;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class BattlePlanTest {

    private static final Point HQ = new Point(0, 0);
    private static final Point ENEMY_HQ = new Point(100, 0);
    private static final List<Post> POSTS = List.of(new Post(0, new Point(20, 0), true, false),
            new Post(1, new Point(60, 0), true, false), new Post(2, new Point(80, 0), false, false));

    @Test
    public void remembersASafeChoiceThenRecommendsTheForwardPostAndAlwaysHasHeadquarters() {
        var safe = Set.<Spawn>of(new Spawn.Headquarters(), new Spawn.Post(0), new Spawn.Post(1), new Spawn.Dropship());
        assertEquals(new Spawn.Post(0), BattlePlan.recommend(POSTS, HQ, ENEMY_HQ, Optional.of(new Spawn.Post(0)), safe::contains));
        assertEquals(new Spawn.Post(1), BattlePlan.recommend(POSTS, HQ, ENEMY_HQ, Optional.of(new Spawn.Headquarters()), safe::contains));
        assertEquals(new Spawn.Post(1), BattlePlan.recommend(POSTS, HQ, ENEMY_HQ, Optional.of(new Spawn.Post(2)), safe::contains));
        assertEquals(new Spawn.Post(1), BattlePlan.recommend(POSTS, HQ, ENEMY_HQ, Optional.of(new Spawn.Dropship()), safe::contains));
        var unsafe = List.of(new Post(0, new Point(20, 0), false, false));
        assertEquals(new Spawn.Headquarters(), BattlePlan.recommend(unsafe, HQ, ENEMY_HQ, Optional.empty(), safe::contains));
        assertEquals(new Spawn.Post(1), BattlePlan.recommend(POSTS, HQ, ENEMY_HQ, Optional.empty(), safe::contains));
    }

    @Test
    public void explicitChoicesWinButLostSpawnsAreRevalidatedIncludingTheRecommendation() {
        var safe = new HashSet<Spawn>(Set.of(new Spawn.Headquarters(), new Spawn.Post(0), new Spawn.Post(1)));
        assertEquals(new Spawn.Headquarters(), BattlePlan.resolve(new Spawn.Headquarters(), safe::contains, new Spawn.Post(1)));
        assertEquals(new Spawn.Post(0), BattlePlan.resolve(new Spawn.Post(0), safe::contains, new Spawn.Post(1)));
        safe.remove(new Spawn.Post(0));
        assertEquals(new Spawn.Post(1), BattlePlan.resolve(new Spawn.Post(0), safe::contains, new Spawn.Post(1)));
        safe.remove(new Spawn.Post(1));
        assertEquals(new Spawn.Headquarters(), BattlePlan.resolve(new Spawn.Post(0), safe::contains, new Spawn.Post(1)));
    }

    @Test
    public void pointsAtAnObjectiveOrTheForwardDefenceWhenEverythingIsSafe() {
        assertEquals(2, BattlePlan.postToHeadFor(POSTS, HQ, ENEMY_HQ).orElseThrow().index());
        assertEquals(1, BattlePlan.postToHeadFor(POSTS.subList(0, 2), HQ, ENEMY_HQ).orElseThrow().index());
    }

    @Test
    public void assaultPriorityWinsOverNearbyObjectivesIncludingWhenItNeedsDefending() {
        var priority = new Post(3, ENEMY_HQ, true, true);
        var posts = List.of(POSTS.getFirst(), POSTS.getLast(), priority);
        assertEquals(priority, BattlePlan.postToHeadFor(posts, HQ, ENEMY_HQ).orElseThrow());
        assertEquals(new Spawn.Post(3), BattlePlan.recommend(posts, HQ, ENEMY_HQ, Optional.empty(), ignored -> true));
        assertEquals(new Spawn.Post(3), BattlePlan.recommend(posts, HQ, ENEMY_HQ, Optional.of(new Spawn.Post(0)), ignored -> true));
    }
}
