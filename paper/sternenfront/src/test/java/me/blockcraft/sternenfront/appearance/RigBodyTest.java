package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.hero.Hero;
import me.blockcraft.sternenfront.reinforcement.Reinforcement;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RigBodyTest {

    private static final Path MODELS = Path.of("../../resourcepack/src/assets/blockcraft/items/sternenfront/body");

    @Test
    public void everyFactionAndRoleHasABodyWhosePartsThePackDraws() {
        for (var faction : Faction.values()) {
            for (var role : CombatClass.values()) {
                var body = RigBody.of(faction, role);
                assertEquals(faction.key() + "_" + role.key(), body.family());
                for (var part : List.of("torso", "head", "arm", "leg", body.leftArm())) {
                    var definition = MODELS.resolve(body.family()).resolve(part + ".json");
                    assertTrue(definition + " is missing; run ./gradlew :resourcepack:generateSternenfrontArt", Files.exists(definition));
                }
            }
        }
    }

    @Test
    public void everyNamedHeroHasAFullBodyAndTheGunHeroesCarryBothPistols() {
        for (var hero : Hero.values()) {
            if (hero == Hero.CHAMPION) {
                continue;
            }
            var body = RigBody.of(hero.faction().orElseThrow(), CombatClass.SOLDIER, Optional.of(hero));
            assertEquals("hero_" + hero.key().replace('-', '_'), body.family());
            assertEquals(hero.weapon() instanceof Hero.Weapon.Gun, body.dualWield());
            for (var part : List.of("torso", "head", "arm", "leg")) {
                assertTrue(body.family() + "/" + part, Files.exists(MODELS.resolve(body.family()).resolve(part + ".json")));
            }
        }
    }

    @Test
    public void reinforcementsHaveTheirOwnFactionBodiesAndGeneratedParts() {
        for (var unit : Reinforcement.values()) {
            var body = RigBody.of(unit);
            assertEquals(unit.faction(), body.faction());
            assertEquals("reinforcement_" + unit.key().replace('-', '_'), body.family());
            assertTrue(body.showsWeapon());
            for (var part : List.of("torso", "head", "arm", "leg")) {
                assertTrue(Files.exists(MODELS.resolve(body.family()).resolve(part + ".json")));
            }
        }
    }

    @Test
    public void aB2TakingTheDarksaberGetsABodyThatDisplaysTheBlade() {
        var body = RigBody.of(Faction.HEGEMONIE, CombatClass.HEAVY, Optional.of(Hero.CHAMPION));
        assertEquals(RigBody.HEGEMONIE_SOLDIER, body);
        assertTrue(body.showsWeapon());
    }
}
