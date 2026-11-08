package me.blockcraft.sternenfront.reinforcement;

import me.blockcraft.sternenfront.config.SternenfrontConfig;
import me.blockcraft.sternenfront.game.Ability;
import me.blockcraft.sternenfront.game.Blaster;
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
            assertEquals(unit, Reinforcement.of(unit.faction()));
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
    public void overridesCanDisableAUnitAndInvalidBodyValuesStayFinite() {
        bundled.set("reinforcement-units.arc-trooper.cost", -10);
        bundled.set("reinforcement-units.arc-trooper.limit", -1);
        bundled.set("reinforcement-units.arc-trooper.health", Double.NaN);
        bundled.set("reinforcement-units.arc-trooper.speed", Double.POSITIVE_INFINITY);
        var units = ReinforcementConfig.from(bundled);
        assertEquals(1, units.stats(ARC_TROOPER).cost());
        assertEquals(0, units.stats(ARC_TROOPER).limit());
        assertTrue(Double.isFinite(units.stats(ARC_TROOPER).health()));
        assertEquals(1, units.stats(ARC_TROOPER).speed(), 0);
        assertEquals(300, units.stats(COMMANDO_DROID).cost());
        assertEquals(26, units.stats(COMMANDO_DROID).health(), 0);
    }

    @Test
    public void aUnitKeepsItsOwnWeaponsAndAbilitiesAndRelicOrRespawnRemovesThem() {
        var heat = new Heat.Rules(8, 3, 40, 0.2, 0.25);
        var fighter = new Fighter(heat);
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
        var choosing = new Fighter.Choosing(10, 30, Optional.empty(), Optional.of(Hero.JEDI)).pick(ARC_TROOPER);
        assertTrue(choosing.hero().isEmpty());
        assertEquals(Optional.of(ARC_TROOPER), choosing.choose(new Spawn.Headquarters()).reinforcement());
        assertTrue(choosing.pick(Optional.of(Hero.COMMANDER)).reinforcement().isEmpty());
        assertTrue(choosing.pick(Optional.empty()).reinforcement().isEmpty());
    }

    @Test
    public void automaticDeploymentCannotChargeAnUnconfirmedUnitAndChangingRoleRequiresConfirmationAgain() {
        var choosing = new Fighter.Choosing(10, 30, Optional.empty(), Optional.empty()).pick(ARC_TROOPER);
        assertTrue(choosing.paidReinforcement().isEmpty());
        assertFalse(choosing.manualReady(30));
        var confirmed = choosing.choose(new Spawn.Headquarters());
        assertEquals(Optional.of(ARC_TROOPER), confirmed.paidReinforcement());
        assertTrue(confirmed.manualReady(10));
        assertTrue(confirmed.pick(ARC_TROOPER).confirmed());
        var hero = confirmed.pick(Optional.of(Hero.JEDI));
        assertFalse(hero.confirmed());
        assertTrue(hero.paidHero().isEmpty());
        assertTrue(hero.paidReinforcement().isEmpty());
        var unit = hero.choose(new Spawn.Headquarters()).pick(ARC_TROOPER);
        assertFalse(unit.confirmed());
        assertTrue(unit.paidReinforcement().isEmpty());
        assertTrue(confirmed.pick(Optional.empty()).paidReinforcement().isEmpty());
    }
}
