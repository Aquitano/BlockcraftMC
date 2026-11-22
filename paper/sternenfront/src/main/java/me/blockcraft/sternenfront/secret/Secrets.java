package me.blockcraft.sternenfront.secret;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.RoundPhaseChangeEvent;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.arena.FrontMap;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import net.kyori.adventure.text.Component;
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
    private final SternenfrontGame game;
    private final Messages messages;
    private FrontMap builtFor;
    private final List<LongConsumer> ticks = new ArrayList<>();
    private final List<Runnable> cleanup = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final List<MusicBox> music = new ArrayList<>();
    private final List<KesselCourse> courses = new ArrayList<>();
    private Optional<BukkitTask> task = Optional.empty();

    private Secrets(JavaPlugin plugin, SternenfrontGame game, Messages messages) {
        this.plugin = plugin;
        this.game = game;
        this.messages = messages;
    }

    /** The secrets of the round's map, from now on if it is known already, else from the round's start. */
    public static Secrets start(JavaPlugin plugin, SternenfrontGame game, Messages messages) {
        var secrets = new Secrets(plugin, game, messages);
        Bukkit.getPluginManager().registerEvents(secrets, plugin);
        if (game.mapChosen()) {
            secrets.build();
        }
        game.feedback().courseStatus(secrets::courseStatus);
        return secrets;
    }

    private void build() {
        var map = game.map();
        builtFor = map;
        var config = SecretsConfig.from(plugin.getConfig());
        var holiday = new MayTheFourth(plugin, messages, map, config.mayTheFourthFactor());
        listen(holiday);
        cleanup.add(holiday::close);
        map.secrets().ifPresent(spots -> {
            var discoveries = new Discoveries(game, messages, config.coins());
            var feedback = BlockcraftApi.get().notifications();
            listen(discoveries);
            if (!spots.crystals().isEmpty()) {
                var crystals = new KyberCrystals(plugin, game, spots.crystals(), discoveries);
                ticks.add(crystals::tick);
                cleanup.add(crystals::close);
            }
            if (!spots.droidPath().isEmpty()) {
                var brummi = new Brummi(plugin, messages, discoveries, spots.droidPath(), config.brummiHits(), config.brummiHideSeconds());
                listen(brummi);
                ticks.add(brummi::tick);
                cleanup.add(brummi::close);
            }
            spots.dune().ifPresent(dune -> {
                var beast = new SandBeast(game, messages, discoveries, dune, config);
                ticks.add(beast::tick);
                cleanup.add(beast::close);
            });
            spots.musicBox().ifPresent(at -> music.add(new MusicBox(game::onField, messages, discoveries::find, feedback::feedback, at, Secret.CANTINA)));
            spots.receiver().ifPresent(at -> music.add(new MusicBox(game::onField, messages, discoveries::find, feedback::feedback, at, Secret.RECEIVER)));
            music.forEach(box -> {
                ticks.add(box::tick);
                cleanup.add(box::stop);
            });
            spots.course().ifPresent(course -> {
                var times = new KesselTimes(BlockcraftApi.get().database());
                times.createTable();
                var kessel = new KesselCourse(plugin, game, messages, discoveries, Optional.of(times),
                        Secret.KESSEL, course, config.parSeconds(), config.limitSeconds());
                listen(kessel);
                courses.add(kessel);
                ticks.add(kessel::tick);
                cleanup.add(kessel::close);
            });
            spots.inspectionCourse().ifPresent(course -> {
                var inspection = new KesselCourse(plugin, game, messages, discoveries, Optional.empty(),
                        Secret.INSPECTION, course, config.inspectionParSeconds(), config.inspectionLimitSeconds());
                listen(inspection);
                courses.add(inspection);
                ticks.add(inspection::tick);
                cleanup.add(inspection::close);
            });
        });
        if (!ticks.isEmpty()) {
            task = Optional.of(Bukkit.getScheduler().runTaskTimer(plugin,
                    () -> ticks.forEach(tick -> tick.accept(Bukkit.getCurrentTick())), 1, 1));
        }
    }

    private void listen(Listener listener) {
        listeners.add(listener);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    public boolean use(Player player, Block block) {
        return music.stream().anyMatch(box -> box.use(player, block, Bukkit.getCurrentTick()));
    }

    private Optional<Component> courseStatus(Player player) {
        return courses.stream().map(course -> course.status(player, Bukkit.getCurrentTick()))
                .flatMap(Optional::stream).findFirst();
    }

    @EventHandler
    public void onPhase(RoundPhaseChangeEvent event) {
        if (event.phase() == GamePhase.ENDING) {
            close();
        } else if (event.phase() == GamePhase.INGAME && builtFor != game.map()) {
            close();
            build();
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
        courses.clear();
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
        builtFor = null;
    }
}
