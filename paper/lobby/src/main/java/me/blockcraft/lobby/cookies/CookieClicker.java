package me.blockcraft.lobby.cookies;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class CookieClicker implements Listener, AutoCloseable {

    private record Stored(CookieProgress.Snapshot progress, long revision) { }
    private record Saved(Stored stored, boolean conflict) { }

    private static final class Session {
        private CookieProgress progress;
        private long edits;
        private long savedEdits;
        private boolean saving;
        private boolean flushRequested;
        private long revision;
        private boolean conflicted;

        private Session(CookieProgress.Rules rules, Stored stored) {
            progress = new CookieProgress(rules, stored.progress());
            revision = stored.revision();
        }
    }

    private final Plugin plugin;
    private final Database database;
    private final Messages messages;
    private final CookieProgress.Rules rules;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final ExecutorService saves = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("lobby-cookies").daemon(true).factory());
    private BukkitTask checkpoint;
    private boolean closed;

    public CookieClicker(Plugin plugin, Database database, Messages messages, CookieProgress.Rules rules) {
        this.plugin = plugin;
        this.database = database;
        this.messages = messages;
        this.rules = rules;
        database.execute("CREATE TABLE IF NOT EXISTS bc_lobby_cookies (player_uuid CHAR(36) PRIMARY KEY, "
                + "cookies BIGINT NOT NULL DEFAULT 0, upgrade_level INT NOT NULL DEFAULT 0, revision BIGINT NOT NULL DEFAULT 0)");
    }

    public void open(Player player, Consumer<Player> back) {
        if (closed) {
            return;
        }
        var id = player.getUniqueId();
        if (sessions.containsKey(id)) {
            new CookieMenu(this, player, back).open(player);
            return;
        }
        BlockcraftApi.get().playerTasks().latest(player, () -> load(id), stored -> {
            if (!closed) {
                sessions.computeIfAbsent(id, ignored -> new Session(rules, stored));
                new CookieMenu(this, player, back).open(player);
            }
        });
    }

    Messages messages() {
        return messages;
    }

    CookieProgress progress(Player player) {
        return sessions.get(player.getUniqueId()).progress;
    }

    long limit() {
        return rules.maxCookies();
    }

    boolean click(Player player, boolean upgrade) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || closed) {
            return false;
        }
        boolean changed = upgrade ? session.progress.upgrade() : session.progress.click(System.nanoTime());
        if (changed) {
            session.edits++;
            scheduleCheckpoint();
            (upgrade ? Sounds.PURCHASE : Sounds.MENU_CLICK).play(player);
        } else if (upgrade) {
            Sounds.MENU_DENY.play(player);
        }
        return changed;
    }

    private void scheduleCheckpoint() {
        if (checkpoint == null && !closed) {
            checkpoint = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                checkpoint = null;
                Map.copyOf(sessions).forEach(this::flush);
            }, rules.saveTicks());
        }
    }

    private Stored load(UUID id) {
        database.update("INSERT IGNORE INTO bc_lobby_cookies (player_uuid) VALUES (?)", id.toString());
        return database.queryOne("SELECT cookies, upgrade_level, revision FROM bc_lobby_cookies WHERE player_uuid = ?",
                row -> new Stored(new CookieProgress.Snapshot(row.getLong(1), row.getInt(2)), row.getLong(3)),
                id.toString()).orElseThrow();
    }

    private Saved save(UUID id, Session session, CookieProgress.Snapshot snapshot) {
        if (database.update("UPDATE bc_lobby_cookies SET cookies = ?, upgrade_level = ?, revision = revision + 1 "
                        + "WHERE player_uuid = ? AND revision = ?", snapshot.cookies(), snapshot.level(), id.toString(), session.revision) == 0) {
            session.conflicted = true;
            return new Saved(load(id), true);
        }
        session.revision++;
        return new Saved(new Stored(snapshot, session.revision), false);
    }

    private void flush(UUID id, Session session) {
        if (session.edits == session.savedEdits) {
            evict(id, session);
            return;
        }
        if (session.saving) {
            session.flushRequested = true;
            return;
        }
        session.saving = true;
        session.flushRequested = false;
        var snapshot = session.progress.snapshot();
        long edits = session.edits;
        saves.execute(() -> {
            try {
                var result = save(id, session, snapshot);
                Tasks.sync(plugin, () -> saved(id, session, edits, result));
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not save cookies for " + id, failure);
                Tasks.sync(plugin, () -> {
                    if (closed) {
                        return;
                    }
                    session.saving = false;
                    var player = plugin.getServer().getPlayer(id);
                    if (player != null) {
                        player.sendMessage(messages.get("cookies.save-failed"));
                    }
                    scheduleCheckpoint();
                });
            }
        });
    }

    private void saved(UUID id, Session session, long edits, Saved result) {
        if (closed) {
            return;
        }
        session.saving = false;
        if (result.conflict()) {
            session.progress = new CookieProgress(rules, result.stored().progress());
            session.revision = result.stored().revision();
            session.conflicted = false;
            session.edits = 0;
            session.savedEdits = 0;
            var player = plugin.getServer().getPlayer(id);
            if (player != null) {
                player.sendMessage(messages.get("cookies.conflict"));
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof CookieMenu menu) {
                    menu.refresh();
                }
            }
        } else {
            session.savedEdits = edits;
        }
        if (session.edits != session.savedEdits && session.flushRequested) {
            flush(id, session);
        } else {
            evict(id, session);
        }
    }

    private void evict(UUID id, Session session) {
        if (!session.saving && session.edits == session.savedEdits && plugin.getServer().getPlayer(id) == null) {
            sessions.remove(id, session);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!closed && event.getInventory().getHolder(false) instanceof CookieMenu) {
            var id = event.getPlayer().getUniqueId();
            var session = sessions.get(id);
            if (session != null) {
                flush(id, session);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var id = event.getPlayer().getUniqueId();
        var session = sessions.get(id);
        if (!closed && session != null) {
            flush(id, session);
            if (!session.saving && session.edits == session.savedEdits) {
                sessions.remove(id, session);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        if (checkpoint != null) {
            checkpoint.cancel();
        }
        sessions.forEach((id, session) -> {
            if (session.edits != session.savedEdits) {
                var snapshot = session.progress.snapshot();
                saves.execute(() -> {
                    try {
                        if (session.conflicted || save(id, session, snapshot).conflict()) {
                            plugin.getLogger().warning("Cookie progress changed on another lobby for " + id + "; kept the stored progress");
                        }
                    } catch (RuntimeException failure) {
                        plugin.getLogger().log(Level.SEVERE, "Could not save cookies during shutdown for " + id, failure);
                    }
                });
            }
        });
        // The API closes the database after Lobby stops; a stalled write must not hold up shutdown indefinitely.
        saves.shutdown();
        try {
            if (!saves.awaitTermination(rules.shutdownSaveSeconds(), TimeUnit.SECONDS)) {
                saves.shutdownNow();
                plugin.getLogger().warning("Cookie saves exceeded the shutdown deadline; some progress may not have been saved");
            }
        } catch (InterruptedException interrupted) {
            saves.shutdownNow();
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while saving cookies during shutdown; some progress may not have been saved");
        }
        sessions.clear();
    }
}
