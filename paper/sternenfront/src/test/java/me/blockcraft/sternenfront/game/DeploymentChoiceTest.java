package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.hero.Hero;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class DeploymentChoiceTest {

    @Test
    public void failedDeploymentRequiresConfirmationAndRearmsAutomaticDeployment() {
        var choosing = new Fighter.Choosing(100, 300, Optional.empty(), Optional.of(Hero.JEDI))
                .choose(new Spawn.Post(1));
        var retry = choosing.retry(650);
        assertFalse(retry.manualReady(350));
        assertTrue(retry.paidHero().isEmpty());
        assertEquals(650, retry.autoTick());
        assertEquals(choosing.chosen(), retry.chosen());
        assertEquals(choosing.hero(), retry.hero());
        assertTrue(retry.choose(new Spawn.Headquarters()).manualReady(350));
    }

    @Test
    public void selectingAHeroDoesNotAuthorizeAutomaticPayment() {
        var choosing = new Fighter.Choosing(100, 300, Optional.empty(), Optional.empty()).pick(Optional.of(Hero.JEDI));
        assertTrue(choosing.paidHero().isEmpty());
        assertFalse(choosing.manualReady(300));
        var confirmed = choosing.choose(new Spawn.Post(1));
        assertFalse(confirmed.manualReady(99));
        assertTrue(confirmed.manualReady(100));
        assertEquals(Optional.of(Hero.JEDI), confirmed.paidHero());
    }

    @Test
    public void changingPaidChoiceKeepsTheSpawnButRequiresNewConfirmation() {
        var choosing = new Fighter.Choosing(100, 300, Optional.empty(), Optional.empty()).choose(new Spawn.Headquarters());
        var hero = choosing.pick(Optional.of(Hero.JEDI));
        assertEquals(choosing.chosen(), hero.chosen());
        assertTrue(hero.paidHero().isEmpty());
        assertFalse(hero.manualReady(100));
        var confirmed = hero.choose(new Spawn.Headquarters());
        assertEquals(Optional.of(Hero.JEDI), confirmed.pick(Optional.of(Hero.JEDI)).paidHero());
        assertTrue(confirmed.pick(Optional.of(Hero.COMMANDER)).paidHero().isEmpty());
        assertTrue(confirmed.pick(Optional.empty()).paidHero().isEmpty());
    }

    @Test
    public void aNewLifeKeepsTheFreeClassWithoutRepurchasingTheLastHero() {
        var fighter = new Fighter(new Heat.Rules(5, 1, 20, 0.2, 0.25));
        fighter.combatClass(CombatClass.MEDIC);
        fighter.hero(Optional.of(Hero.JEDI));
        fighter.choosing(Optional.of(new Fighter.Choosing(100, 50, Optional.empty(), Optional.empty())));
        assertEquals(CombatClass.MEDIC, fighter.combatClass());
        assertTrue(fighter.choosing().orElseThrow().paidHero().isEmpty());
        assertEquals(100, fighter.choosing().orElseThrow().autoTick());
    }
}
