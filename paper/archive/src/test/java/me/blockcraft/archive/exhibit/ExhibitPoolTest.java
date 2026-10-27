package me.blockcraft.archive.exhibit;

import me.blockcraft.api.world.Point;
import org.bukkit.Material;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class ExhibitPoolTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void anExhibitReadsBackAsItWasSaved() throws IOException {
        var pool = pool();
        var exhibit = new Exhibit("neue_lobby", "Neue Lobby", "Vanade", OptionalInt.of(2018), Optional.of("Lobbys"), "Die Lobby von damals.",
                Material.GRASS_BLOCK, Optional.of(new Point(0.5, 65.0, 0.5, 90.0f, 0.0f)),
                List.of(new Checkpoint("Brunnen", "Hier traf man sich.", new Point(10.5, 64.0, -3.5, 180.0f, 15.0f)),
                        new Checkpoint("Turm", "", new Point(-20.0, 90.0, 4.0, 0.0f, 0.0f))));
        withWorld("neue_lobby");
        pool.save(exhibit);

        assertEquals(List.of(exhibit), pool.exhibits());
    }

    @Test
    public void onlyExhibitsWithAValidKeyAConfigAndAWorldAreOnShow() throws IOException {
        write("ready", "origin: Vanade\n");
        withWorld("ready");
        write("no_world", "origin: Vanade\n");
        write("Bad-Key", "origin: Vanade\n");
        withWorld("Bad-Key");
        write("no_origin", "name: Ohne Herkunft\n");
        withWorld("no_origin");

        assertEquals(List.of("ready"), pool().exhibits().stream().map(Exhibit::key).toList());
        assertEquals(Optional.empty(), pool().exhibits().getFirst().category());
    }

    @Test
    public void aCheckpointReplacesTheOneWithItsNameInItsPlace() {
        var here = new Point(1.0, 2.0, 3.0, 0.0f, 0.0f);
        var exhibit = minimal("map", "Vanade", OptionalInt.empty())
                .withCheckpoint(new Checkpoint("A", "", here))
                .withCheckpoint(new Checkpoint("B", "", here))
                .withCheckpoint(new Checkpoint("a", "neu", here));

        assertEquals(List.of(new Checkpoint("a", "neu", here), new Checkpoint("B", "", here)), exhibit.checkpoints());
        assertEquals(List.of("B"), exhibit.withoutCheckpoint("A").checkpoints().stream().map(Checkpoint::name).toList());
    }

    @Test
    public void sectionsGroupByOriginYearAndCategoryWithUndatedLast() {
        var sections = Section.of(List.of(
                categorized("spaet", "Blockcraft", OptionalInt.of(2020), "BedWars"),
                minimal("ohne", "Blockcraft", OptionalInt.empty()),
                categorized("b", "Blockcraft", OptionalInt.of(2016), "BedWars"),
                categorized("a", "Blockcraft", OptionalInt.of(2016), "BedWars"),
                categorized("lobby", "Blockcraft", OptionalInt.of(2016), "Lobbys"),
                categorized("alt", "Vanade", OptionalInt.of(2016), "BedWars")), "Sonstiges");

        assertEquals(List.of("Blockcraft/2016/BedWars", "Vanade/2016/BedWars", "Blockcraft/2016/Lobbys",
                        "Blockcraft/2020/BedWars", "Blockcraft/?/Sonstiges"),
                sections.stream().map(section -> section.origin() + "/"
                        + (section.year().isPresent() ? section.year().getAsInt() : "?") + "/" + section.category()).toList());
        assertEquals(List.of("a", "b"), sections.getFirst().exhibits().stream().map(Exhibit::key).toList());
    }

    @Test
    public void categorySurvivesSetupEditsAndSaving() throws IOException {
        write("map", "origin: Blockcraft\ncategory: SkyWars\n");
        withWorld("map");
        var exhibit = pool().exhibits().getFirst();
        var point = new Point(1.0, 2.0, 3.0, 0.0f, 0.0f);
        pool().save(exhibit.withArrival(point).withCheckpoint(new Checkpoint("A", "", point)).withoutCheckpoint("A"));

        assertEquals(Optional.of("SkyWars"), pool().exhibits().getFirst().category());
        assertEquals(Optional.empty(), minimal("other", "Blockcraft", OptionalInt.empty()).category());
    }

    private static Exhibit categorized(String key, String origin, OptionalInt year, String category) {
        return new Exhibit(key, key, origin, year, Optional.of(category), "", Material.PAPER, Optional.empty(), List.of());
    }

    private ExhibitPool pool() {
        return new ExhibitPool(temporary.getRoot().toPath(), Logger.getAnonymousLogger());
    }

    private void withWorld(String key) throws IOException {
        Files.createDirectories(temporary.getRoot().toPath().resolve(key).resolve("world"));
    }

    private void write(String key, String yaml) throws IOException {
        Path folder = Files.createDirectories(temporary.getRoot().toPath().resolve(key));
        Files.writeString(folder.resolve("exhibit.yml"), yaml);
    }

    private static Exhibit minimal(String key, String origin, OptionalInt year) {
        return new Exhibit(key, key, origin, year, Optional.empty(), "", Material.PAPER, Optional.empty(), List.of());
    }
}
