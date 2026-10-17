package me.blockcraft.api.world;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorldTemplateTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void publishCopiesTheMapButNotThePlayers() throws IOException {
        Path world = folder.newFolder("world").toPath();
        write(world, "level.dat", "level");
        write(world, "region/r.0.0.mca", "chunks");
        write(world, "blockcraft/Lobby/locations.yml", "spawn");
        write(world, "players/data/uuid.dat", "player");
        write(world, "session.lock", "lock");
        Path directory = folder.newFolder("template").toPath();
        write(directory, ".gitkeep", "");
        var template = WorldTemplate.at(directory);
        assertFalse(template.exists());

        template.publish(world);

        assertTrue(template.exists());
        assertEquals("chunks", Files.readString(directory.resolve("region/r.0.0.mca")));
        assertEquals("spawn", Files.readString(directory.resolve("blockcraft/Lobby/locations.yml")));
        assertFalse(Files.exists(directory.resolve("players")));
        assertFalse(Files.exists(directory.resolve("session.lock")));
        assertFalse(Files.exists(directory.resolve(".gitkeep")));
    }

    @Test
    public void restoreReplacesWhatTheWorldHadWithTheTemplate() throws IOException {
        Path directory = folder.newFolder("template").toPath();
        write(directory, "level.dat", "template");
        write(directory, "region/r.0.0.mca", "pristine");
        Path world = folder.newFolder("world").toPath();
        write(world, "level.dat", "played");
        write(world, "region/r.0.0.mca", "broken");
        write(world, "region/r.1.0.mca", "explored");
        write(world, "playerdata/uuid.dat", "player");

        WorldTemplate.at(directory).restore(world);

        assertEquals("template", Files.readString(world.resolve("level.dat")));
        assertEquals("pristine", Files.readString(world.resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(world.resolve("region/r.1.0.mca")));
        assertFalse(Files.exists(world.resolve("playerdata")));
        assertEquals("pristine", Files.readString(directory.resolve("region/r.0.0.mca")));
    }

    @Test
    public void anInterruptedPublishIsNeverRestoredAndClearedByTheNext() throws IOException {
        Path directory = folder.newFolder("template").toPath();
        write(directory, "level.dat", "template");
        write(directory, ".staging/level.dat", "half");
        write(directory, ".replaced/region/r.0.0.mca", "old");
        Path world = folder.newFolder("world").toPath();
        var template = WorldTemplate.at(directory);

        template.restore(world);

        assertEquals("template", Files.readString(world.resolve("level.dat")));
        assertFalse(Files.exists(world.resolve(".staging")));
        assertFalse(Files.exists(world.resolve(".replaced")));

        write(world, "level.dat", "saved");
        template.publish(world);

        assertEquals("saved", Files.readString(directory.resolve("level.dat")));
        assertFalse(Files.exists(directory.resolve(".staging")));
        assertFalse(Files.exists(directory.resolve(".replaced")));
    }

    @Test
    public void aSwapCutShortAfterTheOldMapMovedOutFinishesWithTheNewOne() throws IOException {
        Path directory = folder.newFolder("template").toPath();
        write(directory, "region/r.0.0.mca", "new");
        write(directory, ".staging/level.dat", "new");
        write(directory, ".replaced/level.dat", "old");
        write(directory, ".replaced/region/r.0.0.mca", "old");
        var template = WorldTemplate.at(directory);

        template.recover();

        assertEquals("new", Files.readString(directory.resolve("level.dat")));
        assertEquals("new", Files.readString(directory.resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(directory.resolve(".replaced")));
        assertFalse(Files.exists(directory.resolve(".staging")));
    }

    private static void write(Path root, String file, String content) throws IOException {
        Path path = root.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
