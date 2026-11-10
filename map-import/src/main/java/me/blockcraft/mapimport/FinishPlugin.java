package me.blockcraft.mapimport;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs on the finishing server only. Holds a ticket on every chunk the import listed until the chunk ticks, which is
 * when Paper finishes converting it: generation up to status full with the world's own generator, then the post
 * processing and the upgrade data that fix block shapes against the neighbours. The game stays frozen the whole time,
 * so no scheduled tick, random tick or entity runs and the build stays as it was; then the server stops and saves.
 */
public final class FinishPlugin extends JavaPlugin {

    private static final int BATCH = 256;
    private static final int PATIENCE_TICKS = 20 * 60 * 5;

    private World world;
    private List<ChunkPos> chunks;
    private ImportFiles files;
    private int next;
    private List<ChunkPos> batch = List.of();
    private int waited;
    private BukkitTask task;

    @Override
    public void onEnable() {
        files = ImportFiles.at(Path.of(System.getProperty(ImportFiles.SCRATCH_PROPERTY)));
        Bukkit.getScheduler().runTask(this, this::start);
    }

    private void start() {
        chunks = files.readChunks();
        world = Bukkit.getWorlds().getFirst();
        Bukkit.getServerTickManager().setFrozen(true);
        getLogger().info("Finishing " + chunks.size() + " chunks of " + world.getKey());
        task = Bukkit.getScheduler().runTaskTimer(this, this::tick, 1, 1);
    }

    private void tick() {
        if (!batch.isEmpty() && !batch.stream().allMatch(this::ticking)) {
            if (++waited > PATIENCE_TICKS) {
                getLogger().severe("Chunks did not finish within five minutes, for example "
                        + batch.stream().filter(chunk -> !ticking(chunk)).findFirst().orElseThrow() + "; the import stops here");
                task.cancel();
                Bukkit.shutdown();
            }
            return;
        }
        batch.forEach(chunk -> world.removePluginChunkTicket(chunk.x(), chunk.z(), this));
        if (next >= chunks.size()) {
            task.cancel();
            finish();
            return;
        }
        batch = new ArrayList<>(chunks.subList(next, Math.min(next + BATCH, chunks.size())));
        next += batch.size();
        waited = 0;
        batch.forEach(chunk -> world.addPluginChunkTicket(chunk.x(), chunk.z(), this));
        if (next % (BATCH * 8) == 0 || next == chunks.size()) {
            getLogger().info("Finishing chunks up to " + next + " of " + chunks.size());
        }
    }

    private boolean ticking(ChunkPos chunk) {
        if (!world.isChunkLoaded(chunk.x(), chunk.z())) {
            return false;
        }
        var level = world.getChunkAt(chunk.x(), chunk.z()).getLoadLevel();
        return level == Chunk.LoadLevel.TICKING || level == Chunk.LoadLevel.ENTITY_TICKING;
    }

    private void finish() {
        try {
            Files.writeString(files.finished(), "finished " + chunks.size() + " chunks\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        getLogger().info("Finished all " + chunks.size() + " chunks; stopping, which saves them");
        Bukkit.shutdown();
    }
}
