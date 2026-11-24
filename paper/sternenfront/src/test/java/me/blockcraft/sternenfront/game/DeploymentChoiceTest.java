package me.blockcraft.sternenfront.game;

import me.blockcraft.sternenfront.hero.Hero;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class DeploymentChoiceTest {

    @Test
    public void failedDeploymentRequiresConfirmationAndRearmsAutomaticDeployment() {
        var choosing = Fighter.Choosing.begin(100, 300).pick(Optional.of(Hero.JEDI)).choose(new Spawn.Post(1));
        var retry = choosing.retry(650);
        assertFalse(retry.manualReady(350));
        assertTrue(retry.confirmedHero().isEmpty());
        assertEquals(650, retry.autoTick());
        assertEquals(choosing.chosen(), retry.chosen());
        assertEquals(choosing.hero(), retry.hero());
        assertTrue(retry.choose(new Spawn.Headquarters()).manualReady(350));
    }

    @Test
    public void selectingAHeroDoesNotAuthorizeAutomaticPayment() {
        var choosing = Fighter.Choosing.begin(100, 300).pick(Optional.of(Hero.JEDI));
        assertTrue(choosing.confirmedHero().isEmpty());
        assertFalse(choosing.manualReady(300));
        var confirmed = choosing.choose(new Spawn.Post(1));
        assertFalse(confirmed.manualReady(99));
        assertTrue(confirmed.manualReady(100));
        assertEquals(Optional.of(Hero.JEDI), confirmed.confirmedHero());
    }

    @Test
    public void changingPaidChoiceKeepsTheSpawnButRequiresNewConfirmation() {
        var choosing = Fighter.Choosing.begin(100, 300).choose(new Spawn.Headquarters());
        var hero = choosing.pick(Optional.of(Hero.JEDI));
        assertEquals(choosing.chosen(), hero.chosen());
        assertTrue(hero.confirmedHero().isEmpty());
        assertFalse(hero.manualReady(100));
        var confirmed = hero.choose(new Spawn.Headquarters());
        assertEquals(Optional.of(Hero.JEDI), confirmed.pick(Optional.of(Hero.JEDI)).confirmedHero());
        assertTrue(confirmed.pick(Optional.of(Hero.COMMANDER)).confirmedHero().isEmpty());
        assertTrue(confirmed.pick(Optional.empty()).confirmedHero().isEmpty());
    }

    @Test
    public void aNewLifeKeepsTheFreeClassWithoutRepurchasingTheLastHero() {
        var fighter = new Fighter(new Heat.Rules(5, 1, 20, 0.2, 0.25), ability -> new Charges(1, 1));
        fighter.combatClass(CombatClass.MEDIC);
        fighter.hero(Optional.of(Hero.JEDI));
        fighter.choosing(Optional.of(Fighter.Choosing.begin(100, 50)));
        assertEquals(CombatClass.MEDIC, fighter.combatClass());
        assertTrue(fighter.choosing().orElseThrow().confirmedHero().isEmpty());
        assertEquals(100, fighter.choosing().orElseThrow().autoTick());
    }
}
