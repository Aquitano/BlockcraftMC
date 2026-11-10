package me.blockcraft.sternenfront.arena;

import me.blockcraft.api.pack.PackModel;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ArenaFileTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void yardEnvironmentIsOptionalAndPreservesItsCoordinates() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.set("name", "Sturmwerft");
        yaml.createSection("headquarters");
        yaml.set("play-area", List.of(-97.0, 50.0, -70.0, 97.0, 128.0, 71.0));
        for (var position : List.of("relic", "overview", "lobby", "spectator")) yaml.set(position, "0.5 65 0.5");
        var file = temporary.newFile("arena.yml");
        yaml.save(file);
        assertEquals(YardEnvironment.EMPTY, ArenaFile.load(null, file.toPath()).environment());
        assertEquals(196, ArenaFile.load(null, file.toPath()).worldBorderSize(), 0.001);

        yaml.set("environment.steam", List.of("-10.5 67 -51.5"));
        yaml.set("environment.sparks", List.of("13.5 66.5 -49.5"));
        yaml.set("environment.crane.from", "-10.5 84 0.5");
        yaml.set("environment.crane.to", "10.5 84 0.5");
        yaml.save(file);
        var environment = ArenaFile.load(null, file.toPath()).environment();
        assertEquals(-10.5, environment.steam().getFirst().getX(), 0.001);
        assertEquals(67, environment.steam().getFirst().getY(), 0.001);
        assertEquals(-49.5, environment.sparks().getFirst().getZ(), 0.001);
        assertTrue(environment.crane().isPresent());
        var crane = environment.crane().orElseThrow();
        assertEquals(-10.5, crane.from().getX(), 0.001);
        assertEquals(10.5, crane.to().getX(), 0.001);
        assertEquals(84, crane.from().getY(), 0.001);
    }

    @Test
    public void worldBorderCanIncludeSceneryWithoutChangingThePlayArea() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.createSection("headquarters");
        yaml.set("play-area", List.of(-97.0, 50.0, -70.0, 97.0, 128.0, 71.0));
        for (var position : List.of("relic", "overview", "lobby", "spectator")) yaml.set(position, "0.5 65 0.5");
        var file = temporary.newFile("border.yml");
        yaml.set("world-border-size", 384);
        yaml.save(file);
        var map = ArenaFile.load(null, file.toPath());
        assertEquals(384, map.worldBorderSize(), 0.001);
        assertEquals(194, map.playArea().getWidthX(), 0.001);
        assertEquals(40, map.deathY(), 0.001);

        yaml.set("world-border-size", 100);
        yaml.save(file);
        assertEquals(196, ArenaFile.load(null, file.toPath()).worldBorderSize(), 0.001);
    }

    @Test
    public void seaIsOptInAndKeepsItsWaterlineAndSupportRadius() throws Exception {
        var yaml = secretMap();
        var file = temporary.newFile();
        yaml.save(file);
        assertTrue(ArenaFile.load(null, file.toPath()).environment().sea().isEmpty());
        yaml.set("environment.sea", List.of(Map.of("at", "-65.5 57.05 103.5", "radius", 11.2)));
        yaml.save(file);
        var support = ArenaFile.load(null, file.toPath()).environment().sea().getFirst();
        assertEquals(new Location(null, -65.5, 57.05, 103.5), support.at());
        assertEquals(11.2, support.radius(), 0.001);
        yaml.set("environment.sea", List.of(Map.of("at", "0 57 0", "radius", 0)));
        yaml.save(file);
        assertThrows(IllegalArgumentException.class, () -> ArenaFile.load(null, file.toPath()));
    }

    @Test
    public void yardPropsPreserveTheirModelGroundPositionYawAndScale() throws Exception {
        var yaml = secretMap();
        yaml.set("environment.props", List.of(
                Map.of("kind", "console", "at", "-50.5 83 -104.5 90", "scale", 1.25),
                Map.of("kind", "repair_droid", "at", "1.5 82 116.5 -90 0")));
        var file = temporary.newFile();
        yaml.save(file);
        var environment = ArenaFile.load(null, file.toPath()).environment();
        assertTrue(environment.crane().isEmpty());
        assertTrue(environment.steam().isEmpty());
        assertEquals(2, environment.props().size());
        var console = environment.props().getFirst();
        assertEquals(PackModel.STERNENFRONT_YARD_CONSOLE, console.model());
        assertEquals(new Location(null, -50.5, 83, -104.5, 90, 0), console.at());
        assertEquals(1.25, console.scale(), 0);
        var droid = environment.props().getLast();
        assertEquals(PackModel.STERNENFRONT_YARD_REPAIR_DROID, droid.model());
        assertEquals(-90, droid.at().getYaw(), 0);
        assertEquals(1, droid.scale(), 0);
    }

    @Test
    public void clinicalPropsResolveTheirKindsAtTheDeskScales() throws Exception {
        var yaml = secretMap();
        yaml.set("environment.props", List.of(
                Map.of("kind", "clone_tank", "at", "7.4 82 95 0", "scale", 1.5),
                Map.of("kind", "gene_archive", "at", "9 82 95 0", "scale", .8),
                Map.of("kind", "embryo_rack", "at", "12.5 82 95 0", "scale", 1)));
        var file = temporary.newFile();
        yaml.save(file);
        var props = ArenaFile.load(null, file.toPath()).environment().props();
        assertEquals(List.of(PackModel.STERNENFRONT_YARD_CLONE_TANK, PackModel.STERNENFRONT_YARD_GENE_ARCHIVE,
                PackModel.STERNENFRONT_YARD_EMBRYO_RACK), props.stream().map(YardEnvironment.Prop::model).toList());
        assertEquals(1.5, props.getFirst().scale(), .0001);
        assertEquals(.8, props.get(1).scale(), .0001);
        assertEquals(1, props.getLast().scale(), .0001);
        assertEquals(new Location(null, 7.4, 82, 95, 0, 0), props.getFirst().at());
    }

    @Test
    public void aMapWithMorePostsThanTheHudHasLettersIsRejected() throws Exception {
        var yaml = secretMap();
        var file = temporary.newFile();
        yaml.set("posts", java.util.Collections.nCopies(ArenaFile.MAX_POSTS + 1, Map.of()));
        yaml.save(file);
        var refused = assertThrows(IllegalArgumentException.class, () -> ArenaFile.load(null, file.toPath()));
        assertTrue(refused.getMessage(), refused.getMessage().contains("at most " + ArenaFile.MAX_POSTS));
    }

    @Test
    public void yardPropsRejectUnknownModelsAndInvalidScales() throws Exception {
        var yaml = secretMap();
        var file = temporary.newFile();
        yaml.set("environment.props", List.of(Map.of("kind", "missing", "at", "0 83 0")));
        yaml.save(file);
        assertThrows(IllegalArgumentException.class, () -> ArenaFile.load(null, file.toPath()));
        for (var scale : List.of(0, 2, Double.NaN, "large")) {
            yaml.set("environment.props", List.of(Map.of("kind", "console", "at", "0 83 0", "scale", scale)));
            yaml.save(file);
            assertThrows(IllegalArgumentException.class, () -> ArenaFile.load(null, file.toPath()));
        }
    }

    @Test
    public void receiverDoesNotRequireAnyTessarSecrets() throws Exception {
        var yaml = secretMap();
        yaml.set("secrets.receiver", "12 74 -8");
        var spots = loadSecrets(yaml);
        assertEquals(12, spots.receiver().orElseThrow().getX(), 0);
        assertEquals(74, spots.receiver().orElseThrow().getY(), 0);
        assertTrue(spots.musicBox().isEmpty());
        assertTrue(spots.dune().isEmpty());
        assertTrue(spots.course().isEmpty());
        assertTrue(spots.inspectionCourse().isEmpty());
        assertTrue(spots.crystals().isEmpty());
        assertTrue(spots.droidPath().isEmpty());
    }

    @Test
    public void inspectionCoursePreservesOrderedEastwardGatesWithoutOtherSecrets() throws Exception {
        var yaml = secretMap();
        yaml.set("secrets.inspection-course.lane", List.of(-20, 65, -5, 25, 85, 5));
        yaml.set("secrets.inspection-course.pit-y", 68);
        yaml.set("secrets.inspection-course.gates", List.of(-18, -3, 10, 23));
        yaml.set("secrets.inspection-course.board", "-19 73 1");
        var spots = loadSecrets(yaml);
        var course = spots.inspectionCourse().orElseThrow();
        assertEquals(List.of(-18.0, -3.0, 10.0, 23.0), course.gates());
        assertEquals(68, course.pitY(), 0);
        assertEquals(45, course.lane().getWidthX(), 0);
        assertEquals(-19, course.board().getX(), 0);
        assertTrue(spots.receiver().isEmpty());
        assertTrue(spots.course().isEmpty());
        assertTrue(spots.dune().isEmpty());
    }

    @Test
    public void tessarCoordinatesRemainIndependent() throws Exception {
        var yaml = secretMap();
        yaml.set("secrets.crystals", List.of("1 2 3"));
        yaml.set("secrets.droid-path", List.of("4 5 6", "7 8 9"));
        yaml.set("secrets.music-box", "-36 65 53");
        yaml.set("secrets.dune.top", "20 66 30");
        yaml.set("secrets.dune.radius", 4);
        yaml.set("secrets.course.lane", List.of(-62, 60, 55, -24, 76, 62));
        yaml.set("secrets.course.pit-y", 63);
        yaml.set("secrets.course.gates", List.of(-25.5, -34.5, -54.5, -60.5));
        yaml.set("secrets.course.board", "-24 67 58");
        var spots = loadSecrets(yaml);
        assertEquals(1, spots.crystals().getFirst().getX(), 0);
        assertEquals(2, spots.droidPath().size());
        assertEquals(-36, spots.musicBox().orElseThrow().getX(), 0);
        assertEquals(4, spots.dune().orElseThrow().radius(), 0);
        assertEquals(-60.5, spots.course().orElseThrow().gates().getLast(), 0);
        assertTrue(spots.receiver().isEmpty());
        assertTrue(spots.inspectionCourse().isEmpty());
    }

    @Test
    public void absentOrEmptySecretsNeedNoCoordinates() throws Exception {
        var yaml = secretMap();
        var file = temporary.newFile("absent.yml");
        yaml.save(file);
        assertTrue(ArenaFile.load(null, file.toPath()).secrets().isEmpty());
        yaml.createSection("secrets");
        var spots = loadSecrets(yaml);
        assertTrue(spots.receiver().isEmpty());
        assertTrue(spots.inspectionCourse().isEmpty());
        assertTrue(spots.dune().isEmpty());
        assertTrue(spots.musicBox().isEmpty());
        assertTrue(spots.course().isEmpty());
    }

    private YamlConfiguration secretMap() {
        var yaml = new YamlConfiguration();
        yaml.createSection("headquarters");
        yaml.set("play-area", List.of(-97, 50, -70, 97, 128, 71));
        for (var position : List.of("relic", "overview", "lobby", "spectator")) yaml.set(position, "0.5 65 0.5");
        return yaml;
    }

    private SecretSpots loadSecrets(YamlConfiguration yaml) throws Exception {
        var file = temporary.newFile();
        yaml.save(file);
        return ArenaFile.load(null, file.toPath()).secrets().orElseThrow();
    }

    @Test
    public void battlefieldUsesTheDeathPlaneAndHorizontalPlayAreaBelowScenicAndFlightBounds() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.createSection("headquarters");
        yaml.set("play-area", List.of(-100.0, 50.0, -150.0, 100.0, 144.0, 145.0));
        yaml.set("world-border-size", 384);
        yaml.set("death-y", 60.0);
        for (var position : List.of("relic", "overview", "lobby", "spectator")) yaml.set(position, "0.5 65 0.5");
        var file = temporary.newFile("battlefield.yml");
        yaml.save(file);
        var map = ArenaFile.load(null, file.toPath());
        assertEquals(60, map.deathY(), 0.001);
        assertFalse(map.outsideBattlefield(new Location(null, 97.5, 65, 0)));
        assertFalse(map.outsideBattlefield(new Location(null, 0, 200, 0)));
        assertFalse(map.outsideBattlefield(new Location(null, -100, 60, -150)));
        assertTrue(map.outsideBattlefield(new Location(null, 0, 59.9, 0)));
        assertTrue(map.outsideBattlefield(new Location(null, 0, 46, 0)));
        assertTrue(map.outsideBattlefield(new Location(null, 100, 65, 0)));
        assertTrue(map.outsideBattlefield(new Location(null, -100.1, 65, 0)));
        assertTrue(map.outsideBattlefield(new Location(null, 0, 65, 145)));
        assertTrue(map.outsideBattlefield(new Location(null, 0, 65, -150.1)));
    }
}
