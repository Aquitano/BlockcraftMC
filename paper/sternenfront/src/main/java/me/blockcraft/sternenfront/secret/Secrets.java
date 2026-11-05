package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.RoundPhaseChangeEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;

public final class Secrets implements Listener, AutoCloseable {

    private final JavaPlugin plugin;
    private final List<LongConsumer> ticks = new ArrayList<>();
    private final List<Runnable> cleanup = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final List<MusicBox> music = new ArrayList<>();
    private Optional<BukkitTask> task = Optional.empty();

    private Secrets(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static Secrets start(JavaPlugin plugin, SternenfrontGame game, Messages messages) {
        var secrets = new Secrets(plugin);
        var config = SecretsConfig.from(plugin.getConfig());
        secrets.listen(secrets);
        var holiday = new MayTheFourth(plugin, messages, game.map(), config.mayTheFourthFactor());
        secrets.listen(holiday);
        secrets.cleanup.add(holiday::close);
        game.map().secrets().ifPresent(spots -> {
            var discoveries = new Discoveries(messages, config.coins());
            var feedback = BlockcraftApi.get().notifications();
            secrets.listen(discoveries);
            if (!spots.crystals().isEmpty()) {
                var crystals = new KyberCrystals(plugin, game, spots.crystals(), discoveries);
                secrets.ticks.add(crystals::tick);
                secrets.cleanup.add(crystals::close);
            }
            if (!spots.droidPath().isEmpty()) {
                var brummi = new Brummi(plugin, messages, discoveries, spots.droidPath(), config.brummiHits(), config.brummiHideSeconds());
                secrets.listen(brummi);
                secrets.ticks.add(brummi::tick);
                secrets.cleanup.add(brummi::close);
            }
            spots.dune().ifPresent(dune -> {
                var beast = new SandBeast(game, messages, discoveries, dune, config);
                secrets.ticks.add(beast::tick);
                secrets.cleanup.add(beast::close);
            });
            spots.musicBox().ifPresent(at -> secrets.music.add(new MusicBox(game::onField, messages, discoveries::find, feedback::feedback, at, Secret.CANTINA)));
            spots.receiver().ifPresent(at -> secrets.music.add(new MusicBox(game::onField, messages, discoveries::find, feedback::feedback, at, Secret.RECEIVER)));
            secrets.music.forEach(box -> {
                secrets.ticks.add(box::tick);
                secrets.cleanup.add(box::stop);
            });
            spots.course().ifPresent(course -> {
                var times = new KesselTimes(BlockcraftApi.get().database());
                times.createTable();
                var kessel = new KesselCourse(plugin, game, messages, discoveries, Optional.of(times),
                        Secret.KESSEL, course, config.parSeconds(), config.limitSeconds());
                secrets.listen(kessel);
                secrets.ticks.add(kessel::tick);
                secrets.cleanup.add(kessel::close);
            });
            spots.inspectionCourse().ifPresent(course -> {
                var inspection = new KesselCourse(plugin, game, messages, discoveries, Optional.empty(),
                        Secret.INSPECTION, course, config.inspectionParSeconds(), config.inspectionLimitSeconds());
                secrets.listen(inspection);
                secrets.ticks.add(inspection::tick);
                secrets.cleanup.add(inspection::close);
            });
        });
        if (!secrets.ticks.isEmpty()) {
            secrets.task = Optional.of(Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> secrets.ticks.forEach(tick -> tick.accept(Bukkit.getCurrentTick())), 1, 1));
        }
        return secrets;
    }

    private void listen(Listener listener) {
        listeners.add(listener);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    public boolean use(Player player, Block block) {
        return music.stream().anyMatch(box -> box.use(player, block, Bukkit.getCurrentTick()));
    }

    @EventHandler
    public void onPhase(RoundPhaseChangeEvent event) {
        if (event.phase() == GamePhase.ENDING) {
            close();
        }
    }

    @Override
    public void close() {
        task.ifPresent(BukkitTask::cancel);
        task = Optional.empty();
        cleanup.forEach(Runnable::run);
        cleanup.clear();
        ticks.clear();
        music.clear();
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }
}
