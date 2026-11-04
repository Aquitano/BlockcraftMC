package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BattleMusic {

    enum Cue {
        OPENING("opening", 24), BATTLE("battle", 92), PRESSURE("pressure", 75),
        VICTORY("victory", 12), DEFEAT("defeat", 12);

        private final String key;
        private final int ticks;

        Cue(String key, int seconds) {
            this.key = "blockcraft:sternenfront.music." + key;
            this.ticks = seconds * 20;
        }
    }

    private record Playing(Cue cue, long next) { }

    private final SternenfrontGame game;
    private final Map<UUID, Playing> listeners = new HashMap<>();
    private final Map<UUID, Cue> finale = new HashMap<>();
    private BukkitTask finaleRetry;
    private final boolean enabled;
    private final float volume;

    BattleMusic(SternenfrontGame game) {
        this.game = game;
        enabled = game.plugin().getConfig().getBoolean("music.enabled", true);
        volume = (float) Math.clamp(game.plugin().getConfig().getDouble("music.volume", 0.35), 0, 1);
    }

    static Cue cue(long tick, int secondsLeft, int lowestTickets, int pressureTickets, boolean lastStand) {
        return lastStand || lowestTickets <= pressureTickets || secondsLeft <= 90 ? Cue.PRESSURE : tick < 24 * 20 ? Cue.OPENING : Cue.BATTLE;
    }

    void tick(long tick, int secondsLeft, int lowestTickets, boolean lastStand) {
        if (tick % 20 != 0 || !enabled || volume == 0) {
            return;
        }
        int threshold = Math.max(1, game.plugin().getConfig().getInt("music.pressure-tickets", 35));
        var wanted = cue(tick, secondsLeft, lowestTickets, threshold, lastStand);
        for (var player : Bukkit.getOnlinePlayers()) {
            // A cue sent while the client is still loading the pack is lost, and the next one is a whole cue away.
            if (!player.getWorld().equals(game.map().world()) || !BlockcraftApi.get().resourcePack().applied(player)) {
                forget(player);
                continue;
            }
            var playing = listeners.get(player.getUniqueId());
            if (playing == null || tick >= playing.next() || wanted == Cue.PRESSURE && playing.cue() != wanted) {
                play(player, wanted);
                listeners.put(player.getUniqueId(), new Playing(wanted, tick + wanted.ticks + 300));
            }
        }
        listeners.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    void finish(Optional<Faction> winner) {
        clear();
        if (enabled && volume > 0) {
            for (var player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(game.map().world())) {
                    finale.put(player.getUniqueId(), winner.filter(faction -> game.factionOf(player).filter(faction::equals).isPresent())
                            .map(ignored -> Cue.VICTORY).orElse(Cue.DEFEAT));
                }
            }
            playFinale();
            if (!finale.isEmpty()) {
                finaleRetry = Bukkit.getScheduler().runTaskTimer(game.plugin(), this::playFinale, 20L, 20L);
            }
        }
    }

    /** A client still loading the pack would lose the cue, and nothing later replaces it, so it waits for the pack. */
    private void playFinale() {
        finale.entrySet().removeIf(entry -> {
            var player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                return true;
            }
            if (!BlockcraftApi.get().resourcePack().applied(player)) {
                return false;
            }
            play(player, entry.getValue());
            listeners.put(player.getUniqueId(), new Playing(entry.getValue(), Long.MAX_VALUE));
            return true;
        });
        if (finale.isEmpty() && finaleRetry != null) {
            finaleRetry.cancel();
            finaleRetry = null;
        }
    }

    private void play(Player player, Cue cue) {
        forget(player);
        player.playSound(net.kyori.adventure.sound.Sound.sound(net.kyori.adventure.key.Key.key(cue.key),
                net.kyori.adventure.sound.Sound.Source.MUSIC, volume, 1));
    }

    public void forget(Player player) {
        var playing = listeners.remove(player.getUniqueId());
        if (playing != null) {
            player.stopSound(playing.cue().key, SoundCategory.MUSIC);
        }
    }

    void clear() {
        Bukkit.getOnlinePlayers().forEach(this::forget);
        listeners.clear();
        finale.clear();
        if (finaleRetry != null) {
            finaleRetry.cancel();
            finaleRetry = null;
        }
    }
}
