package me.blockcraft.replay;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.replay.playback.PlaybackManager;
import me.blockcraft.replay.recording.MatchRecorder;
import me.blockcraft.replay.storage.ReplayStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.logging.Level;

public final class ReplayPlugin extends JavaPlugin {

    public static final String USE_PERMISSION = "blockcraft.replay.use";
    public static final String STAFF_PERMISSION = "blockcraft.replay.staff";

    private ReplayStore store;
    private MatchRecorder recorder;
    private PlaybackManager playback;
    private ReplayService service;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = ReplayConfig.load(getConfig(), getDataPath());
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), BlockcraftApi.get().tags());
        store = new ReplayStore(config.directory(), config.limits(), config.maxStoredBytes(), config.minFreeBytes());
        var repository = new ReplayRepository(BlockcraftApi.get().database());
        playback = new PlaybackManager(this, config.limits(), messages);
        service = new ReplayService(this, repository, store, playback, config, messages);
        var controls = new ReplayControls(service, playback, messages);
        service.controls(controls);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new CommandRegistry(event.registrar(), messages).register(ReplayCommand.create(service, messages)));
        Tasks.async(this, () -> {
            repository.initialize();
            return true;
        }, initialized -> {
            service.ready();
            getServer().getPluginManager().registerEvents(service, this);
            getServer().getPluginManager().registerEvents(controls, this);
            if (!BlockcraftApi.get().server().group().equals(config.playbackGroup())) {
                recorder = new MatchRecorder(this, store, config.limits(), service::publish);
                getServer().getPluginManager().registerEvents(recorder, this);
            }
            getServer().getScheduler().runTaskTimer(this, service::maintenance, 20L,
                    config.cleanupInterval().toSeconds() * 20);
        }, () -> getServer().getPluginManager().disablePlugin(this));
    }

    @Override
    public void onDisable() {
        if (recorder != null) {
            recorder.close();
        }
        if (store != null) {
            try {
                store.close();
            } catch (IOException error) {
                getLogger().log(Level.SEVERE, "Replay storage could not finish shutdown", error);
            }
        }
        if (service != null) {
            service.close();
        }
        if (playback != null) {
            playback.close();
        }
    }
}
