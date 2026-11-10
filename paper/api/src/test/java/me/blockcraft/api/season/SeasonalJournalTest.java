package me.blockcraft.api.season;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SeasonalJournalTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void aNewProcessCanRecoverEveryOriginalAndAppliedState() throws IOException {
        var directory = folder.newFolder().toPath();
        var chunk = new SeasonalJournal.Chunk(-61, 102);
        var changes = List.of(new SeasonalJournal.Change(new SeasonalLayout.Point(-967, 27, 1635),
                "minecraft:water[level=0]", "minecraft:ice"));
        new SeasonalJournal(directory).write(chunk, changes);

        var restarted = new SeasonalJournal(directory);
        assertEquals(changes, restarted.read().get(chunk));
        assertTrue(changes.getFirst().restores("minecraft:ice"));
        assertFalse(changes.getFirst().restores("minecraft:oak_planks"));
        restarted.remove(chunk);
        assertTrue(new SeasonalJournal(directory).read().isEmpty());
    }

    @Test
    public void anUnfinishedStagingFileDoesNotReplaceTheLastCompleteJournal() throws IOException {
        var directory = folder.newFolder().toPath();
        var chunk = new SeasonalJournal.Chunk(0, -1);
        var original = List.of(new SeasonalJournal.Change(new SeasonalLayout.Point(2, 40, -3),
                "minecraft:air", "minecraft:snow[layers=1]"));
        var journal = new SeasonalJournal(directory);
        journal.write(chunk, original);
        Files.writeString(directory.resolve("0.-1.dat.tmp"), "interrupted write");

        assertEquals(original, new SeasonalJournal(directory).read().get(chunk));
    }
}
