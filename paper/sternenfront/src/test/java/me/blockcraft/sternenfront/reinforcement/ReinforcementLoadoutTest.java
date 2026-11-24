package me.blockcraft.sternenfront.reinforcement;

import me.blockcraft.sternenfront.config.SternenfrontConfig;
import me.blockcraft.sternenfront.game.Ability;
import me.blockcraft.sternenfront.game.Blaster;
import me.blockcraft.sternenfront.game.Charges;
import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.Fighter;
import me.blockcraft.sternenfront.game.Heat;
import me.blockcraft.sternenfront.game.SoundCue;
import me.blockcraft.sternenfront.game.Spawn;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.hero.HeroConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static me.blockcraft.sternenfront.reinforcement.Reinforcement.ARC_TROOPER;
import static me.blockcraft.sternenfront.reinforcement.Reinforcement.COMMANDO_DROID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ReinforcementLoadoutTest {

    private final YamlConfiguration bundled = YamlConfiguration.loadConfiguration(Path.of("src/main/resources/config.yml").toFile());

    @Test
    public void bundledUnitsHaveDistinctRolesBetweenInfantryAndHeroes() {
        var units = ReinforcementConfig.from(bundled);
        var combat = SternenfrontConfig.from(bundled);
        var heroes = HeroConfig.from(bundled);
        assertEquals(250, units.stats(ARC_TROOPER).cost());
        assertEquals(300, units.stats(COMMANDO_DROID).cost());
        for (var unit : Reinforcement.values()) {
            assertEquals(unit.faction(), Reinforcement.of(unit.faction()).faction());
            assertTrue(units.stats(unit).cost() < heroes.cost());
            assertTrue(units.stats(unit).health() > combat.stats(unit.faction(), CombatClass.SOLDIER).health());
            assertTrue(units.stats(unit).health() < heroes.health(Hero.JEDI));
            assertEquals(2, units.stats(unit).limit());
        }
        var support = combat.stats(ARC_TROOPER.blaster());
        var close = combat.stats(COMMANDO_DROID.blaster());
        assertTrue(support.heat() < combat.stats(Blaster.REPEATER).heat());
        assertTrue(support.range() > close.range());
        assertTrue(close.damage() > combat.stats(Blaster.E5).damage());
        assertTrue(units.stats(COMMANDO_DROID).speed() > units.stats(ARC_TROOPER).speed());
        assertEquals(List.of(Ability.SHIELD_WALL, Ability.ADRENALINE), ARC_TROOPER.abilities());
        assertEquals(List.of(Ability.JUMP_PACK, Ability.GRENADE), COMMANDO_DROID.abilities());
        assertEquals(SoundCue.BLASTER_REPEATER, ARC_TROOPER.blaster().shotSound());
        assertEquals(SoundCue.BLASTER_CARBINE, COMMANDO_DROID.blaster().shotSound());
    }

    @Test
    public void cloneCommandoIsAnAdditionalRepublicChoiceWithItsOwnWeapon() {
        var fighter = new Fighter(new Heat.Rules(10, 5, 40, 0.2, 0.25), ability -> new Charges(1, 20));
        assertEquals(ARC_TROOPER, fighter.reinforcementChoice(Faction.KOLONIEN));
        fighter.cycleReinforcement(Faction.KOLONIEN);
        assertEquals(Reinforcement.CLONE_COMMANDO, fighter.reinforcementChoice(Faction.KOLONIEN));
        assertEquals(COMMANDO_DROID, fighter.reinforcementChoice(Faction.HEGEMONIE));
        fighter.reinforcement(Optional.of(Reinforcement.CLONE_COMMANDO));
        assertEquals(Optional.of(Blaster.DC17M), fighter.blaster(Faction.KOLONIEN));
        fighter.cycleReinforcement(Faction.KOLONIEN);
        assertEquals(ARC_TROOPER, fighter.reinforcementChoice(Faction.KOLONIEN));
    }

    @Test
    public void overridesCanDisableAUnitClampItsBodyAndNotMakeItInfinite() {
        bundled.set("reinforcement-units.arc-trooper.cost", -10);
        bundled.set("reinforcement-units.arc-trooper.limit", -1);
        bundled.set("reinforcement-units.arc-trooper.health", 500);
        bundled.set("reinforcement-units.arc-trooper.speed", 0);
        var units = ReinforcementConfig.from(bundled);
        assertEquals(1, units.stats(ARC_TROOPER).cost());
        assertEquals(0, units.stats(ARC_TROOPER).limit());
        assertEquals(100, units.stats(ARC_TROOPER).health(), 0);
        assertEquals(0.1, units.stats(ARC_TROOPER).speed(), 0);
        bundled.set("reinforcement-units.arc-trooper.health", Double.NaN);
        assertThrows(IllegalStateException.class, () -> ReinforcementConfig.from(bundled));
        assertEquals(300, units.stats(COMMANDO_DROID).cost());
        assertEquals(26, units.stats(COMMANDO_DROID).health(), 0);
    }

    @Test
    public void aUnitKeepsItsOwnWeaponsAndAbilitiesAndRelicOrRespawnRemovesThem() {
        var heat = new Heat.Rules(8, 3, 40, 0.2, 0.25);
        var fighter = new Fighter(heat, ability -> new Charges(1, 1));
        fighter.combatClass(CombatClass.MEDIC);
        fighter.reinforcement(Optional.of(COMMANDO_DROID));
        assertFalse(fighter.hero().isPresent());
        assertEquals(Optional.of(Blaster.COMMANDO_CARBINE), fighter.blaster(Faction.HEGEMONIE));
        assertEquals(COMMANDO_DROID.abilities(), fighter.abilities(Faction.HEGEMONIE));
        fighter.combatClass(CombatClass.HEAVY);
        assertEquals(COMMANDO_DROID.abilities(), fighter.abilities(Faction.HEGEMONIE));
        fighter.hero(Optional.of(Hero.CHAMPION));
        assertFalse(fighter.reinforcement().isPresent());
        assertFalse(fighter.blaster(Faction.HEGEMONIE).isPresent());
        assertTrue(fighter.abilities(Faction.HEGEMONIE).isEmpty());
        fighter.reinforcement(Optional.of(ARC_TROOPER));
        assertFalse(fighter.hero().isPresent());
        fighter.deployed(heat, 60, Optional.empty());
        assertFalse(fighter.reinforcement().isPresent());
        assertEquals(Optional.of(Blaster.REPEATER), fighter.blaster(Faction.KOLONIEN));
    }

    @Test
    public void choosingAClassOrHeroReplacesTheUnitAndChoosingASpawnKeepsIt() {
        var choosing = Fighter.Choosing.begin(10, 30).pick(Optional.of(Hero.JEDI)).pick(ARC_TROOPER);
        assertTrue(choosing.hero().isEmpty());
        assertEquals(Optional.of(ARC_TROOPER), choosing.choose(new Spawn.Headquarters()).reinforcement());
        assertTrue(choosing.pick(Optional.of(Hero.COMMANDER)).reinforcement().isEmpty());
        assertTrue(choosing.pick(Optional.empty()).reinforcement().isEmpty());
    }

    @Test
    public void automaticDeploymentCannotChargeAnUnconfirmedUnitAndChangingRoleRequiresConfirmationAgain() {
        var choosing = Fighter.Choosing.begin(10, 30).pick(ARC_TROOPER);
        assertTrue(choosing.confirmedReinforcement().isEmpty());
        assertFalse(choosing.manualReady(30));
        var confirmed = choosing.choose(new Spawn.Headquarters());
        assertEquals(Optional.of(ARC_TROOPER), confirmed.confirmedReinforcement());
        assertTrue(confirmed.manualReady(10));
        assertTrue(confirmed.pick(ARC_TROOPER).confirmed());
        var hero = confirmed.pick(Optional.of(Hero.JEDI));
        assertFalse(hero.confirmed());
        assertTrue(hero.confirmedHero().isEmpty());
        assertTrue(hero.confirmedReinforcement().isEmpty());
        var unit = hero.choose(new Spawn.Headquarters()).pick(ARC_TROOPER);
        assertFalse(unit.confirmed());
        assertTrue(unit.confirmedReinforcement().isEmpty());
        assertTrue(confirmed.pick(Optional.empty()).confirmedReinforcement().isEmpty());
    }
}
