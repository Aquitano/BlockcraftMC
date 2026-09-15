package me.blockcraft.signs;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.signs.command.CreateSignCommand;
import me.blockcraft.signs.listener.SignClickListener;
import me.blockcraft.signs.sign.SignRenderer;
import me.blockcraft.signs.sign.SignStore;
import me.blockcraft.signs.sign.StatusSigns;
import org.bukkit.plugin.java.JavaPlugin;

public final class StatusSignsPlugin extends JavaPlugin {

    private StatusSigns signs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = YamlFile.loadWithDefaults(getDataPath().resolve("config.yml"), () -> getResource("config.yml"));
        var refresh = YamlFile.section(config, "refresh");
        long intervalTicks = ((Number) refresh.getOrDefault("interval-ticks", 50)).longValue();
        int staleSeconds = ((Number) refresh.getOrDefault("stale-seconds", 20)).intValue();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), BlockcraftApi.get().tags());

        signs = new StatusSigns(this,
                new SignStore(BlockcraftApi.get().map().file(this, "signs.yml"), getLogger()),
                BlockcraftApi.get().servers(),
                staleSeconds,
                new SignRenderer(messages));
        getLogger().info(signs.load() + " signs loaded");
        signs.start(intervalTicks);

        getServer().getPluginManager().registerEvents(new SignClickListener(this, signs, messages), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new CommandRegistry(event.registrar(), messages).register(CreateSignCommand.create(signs, messages)));
    }

    @Override
    public void onDisable() {
        if (signs != null) {
            signs.stop();
        }
    }
}
