package me.blockcraft.story.campaign;

import me.blockcraft.common.db.DatabaseException;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Saves one campaign off the main thread, one save after the other, each over the revision the last stored save
 * wrote. A failed save is tried again, which is safe because {@link Campaigns#save} recognises a save that went
 * through; once the campaign has moved on elsewhere, this server stops saving it.
 */
public final class Saves implements AutoCloseable {

    public enum Outcome { SAVED, FAILED, TAKEN }

    private static final int TRIES = 3;

    private final Campaigns campaigns;
    private final Logger log;
    private final UUID id;
    /** Marks this server's saves, so that a save another server stored the same way is not taken for its own. */
    private final UUID writer = UUID.randomUUID();
    private final ExecutorService queue = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("story-saves").daemon().factory());
    private volatile Campaign stored;
    /** A save that failed without an answer, so it may have gone through after all. */
    private Optional<Campaign> unsure = Optional.empty();
    private boolean taken;
    private final Set<UUID> rejoining = new LinkedHashSet<>();

    public Saves(Campaigns campaigns, Campaign campaign, Logger log) {
        this.campaigns = campaigns;
        this.log = log;
        this.id = campaign.id();
        this.stored = campaign;
    }

    public UUID id() {
        return id;
    }

    /** The revision of the last save that went through. */
    public int revision() {
        return stored.revision();
    }

    public CompletableFuture<Void> rejoin(UUID member) {
        return CompletableFuture.runAsync(() -> {
            if (taken) return;
            rejoining.add(member);
            selectRejoined();
        }, queue);
    }

    private void selectRejoined() {
        for (var member : Set.copyOf(rejoining)) {
            campaigns.rejoin(id, member, writer);
            rejoining.remove(member);
        }
    }

    public CompletableFuture<Outcome> save(Map<String, String> facts, Optional<String> ending, Collection<UUID> members) {
        var snapshot = Map.copyOf(facts);
        var who = Set.copyOf(members);
        return CompletableFuture.supplyAsync(() -> write(snapshot, ending, who), queue);
    }

    private Outcome write(Map<String, String> facts, Optional<String> ending, Set<UUID> members) {
        if (taken) return Outcome.TAKEN;
        for (int attempt = 1; attempt <= TRIES; attempt++) {
            Optional<Campaign> sent = Optional.empty();
            try {
                settle();
                selectRejoined();
                var next = stored.next(facts, ending);
                sent = Optional.of(next);
                if (!campaigns.save(stored, next, members, writer)) {
                    taken = true;
                    log.warn("Campaign {} was saved past revision {} elsewhere, so this server no longer saves it", id, stored.revision());
                    return Outcome.TAKEN;
                }
                stored = next;
                return Outcome.SAVED;
            } catch (DatabaseException failure) {
                if (sent.isPresent()) unsure = sent;
                log.warn("Saving campaign {} failed, try {} of {}", id, attempt, TRIES, failure);
                if (attempt < TRIES && !pause(attempt)) break;
            }
        }
        return Outcome.FAILED;
    }

    /** Takes an unsure save as stored when the database holds it, so the next save builds on it instead of clashing. */
    private void settle() {
        if (unsure.isEmpty()) return;
        if (campaigns.stored(id, writer).equals(unsure)) stored = unsure.get();
        unsure = Optional.empty();
    }

    private static boolean pause(int attempt) {
        try {
            Thread.sleep(500L * attempt);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Lets the saves already asked for finish, for a while, so a stopping server keeps its last checkpoint. */
    @Override
    public void close() {
        queue.shutdown();
        try {
            if (!queue.awaitTermination(10, TimeUnit.SECONDS)) log.warn("Campaign {} still had saves running at shutdown", id);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
