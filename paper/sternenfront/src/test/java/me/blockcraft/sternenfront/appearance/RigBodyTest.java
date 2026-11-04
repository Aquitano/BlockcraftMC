package me.blockcraft.sternenfront.appearance;

import me.blockcraft.sternenfront.game.CombatClass;
import me.blockcraft.sternenfront.game.Faction;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
}
