package me.blockcraft.sternenfront.reinforcement;

import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.hero.BattlePoints;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.hero.HeroRoster;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static me.blockcraft.sternenfront.reinforcement.Reinforcement.ARC_TROOPER;
import static me.blockcraft.sternenfront.reinforcement.Reinforcement.COMMANDO_DROID;
import static me.blockcraft.sternenfront.reinforcement.ReinforcementRoster.Deployment.DEPLOYED;
import static me.blockcraft.sternenfront.reinforcement.ReinforcementRoster.Deployment.FAILED;
import static me.blockcraft.sternenfront.reinforcement.ReinforcementRoster.Deployment.UNAVAILABLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReinforcementRosterTest {

    private final BattlePoints points = new BattlePoints();
    private final ReinforcementConfig config = ReinforcementConfig.from(new MemoryConfiguration());
    private final ReinforcementRoster roster = new ReinforcementRoster(config, points);

    @Test
    public void onlyAnEligibleAffordableDeploymentSpendsPointsOnce() {
        var player = UUID.randomUUID();
        var deployed = new AtomicInteger();
        int cost = config.stats(ARC_TROOPER).cost();
        points.earn(player, cost - 1);
        assertEquals(UNAVAILABLE, roster.deploy(player, Faction.KOLONIEN, ARC_TROOPER, () -> deployed.incrementAndGet() > 0));
        points.earn(player, 1);
        assertEquals(UNAVAILABLE, roster.deploy(player, Faction.HEGEMONIE, ARC_TROOPER, () -> deployed.incrementAndGet() > 0));
        assertEquals(0, deployed.get());
        assertEquals(cost, points.of(player));
        assertEquals(DEPLOYED, roster.deploy(player, Faction.KOLONIEN, ARC_TROOPER, () -> deployed.incrementAndGet() > 0));
        assertEquals(0, points.of(player));
        points.earn(player, cost);
        assertEquals(UNAVAILABLE, roster.deploy(player, Faction.KOLONIEN, ARC_TROOPER, () -> deployed.incrementAndGet() > 0));
        assertEquals(1, deployed.get());
        assertEquals(cost, points.of(player));
    }

    @Test
    public void failedDeploymentRestoresBothBalanceAndSlot() {
        var player = UUID.randomUUID();
        points.earn(player, 600);
        assertEquals(FAILED, roster.deploy(player, Faction.KOLONIEN, ARC_TROOPER, () -> false));
        assertEquals(600, points.of(player));
        assertEquals(0, roster.count(ARC_TROOPER));
        assertEquals(DEPLOYED, roster.deploy(player, Faction.KOLONIEN, ARC_TROOPER, () -> true));
        assertEquals(600 - config.stats(ARC_TROOPER).cost(), points.of(player));
    }

    @Test
    public void aFilledRosterRejectsAStaleSelectionWithoutChargingAndLeavesHeroSlotsIndependent() {
        var heroes = new HeroRoster(1);
        assertTrue(heroes.enlist(UUID.randomUUID(), Faction.KOLONIEN, Hero.JEDI));
        var waiting = UUID.randomUUID();
        points.earn(waiting, 600);
        assertTrue(roster.affordable(waiting, Faction.KOLONIEN, ARC_TROOPER));
        for (int i = 0; i < config.stats(ARC_TROOPER).limit(); i++) {
            var player = UUID.randomUUID();
            points.earn(player, 600);
            assertEquals(DEPLOYED, roster.deploy(player, Faction.KOLONIEN, ARC_TROOPER, () -> true));
        }
        assertEquals(UNAVAILABLE, roster.deploy(waiting, Faction.KOLONIEN, ARC_TROOPER, () -> true));
        assertEquals(600, points.of(waiting));
        assertTrue(roster.open(Faction.HEGEMONIE, COMMANDO_DROID));
        heroes.discharge(heroes.all().keySet().iterator().next());
        assertTrue(heroes.open(Faction.KOLONIEN, Hero.COMMANDER));
        assertFalse(roster.open(Faction.KOLONIEN, ARC_TROOPER));
    }

    @Test
    public void dischargeAndRoundClearReleaseSlotsWithoutRefundingAPlayedLife() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        points.earn(first, 600);
        points.earn(second, 600);
        roster.deploy(first, Faction.KOLONIEN, ARC_TROOPER, () -> true);
        roster.deploy(second, Faction.KOLONIEN, ARC_TROOPER, () -> true);
        roster.discharge(first);
        roster.discharge(first);
        assertEquals(1, roster.count(ARC_TROOPER));
        assertEquals(600 - config.stats(ARC_TROOPER).cost(), points.of(first));
        roster.clear();
        assertEquals(0, roster.count(ARC_TROOPER));
        assertTrue(roster.open(Faction.KOLONIEN, ARC_TROOPER));
    }

    @Test
    public void aDeploymentCallbackCannotSpendTheSameBalanceOrSlotAgain() {
        var player = UUID.randomUUID();
        points.earn(player, 600);
        assertEquals(DEPLOYED, roster.deploy(player, Faction.KOLONIEN, ARC_TROOPER, () -> {
            assertEquals(UNAVAILABLE, roster.deploy(player, Faction.KOLONIEN, ARC_TROOPER, () -> true));
            return true;
        }));
        assertEquals(1, roster.count(ARC_TROOPER));
        assertEquals(600 - config.stats(ARC_TROOPER).cost(), points.of(player));
    }
}
