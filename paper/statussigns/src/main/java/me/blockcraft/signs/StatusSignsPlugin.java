package me.blockcraft.signs;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.signs.command.CreateSignCommand;
import me.blockcraft.signs.listener.SignClickListener;
import me.blockcraft.signs.sign.SignRenderer;
import me.blockcraft.signs.sign.SignStore;
import me.blockcraft.signs.sign.StatusPinger;
import me.blockcraft.signs.sign.StatusSigns;
import org.bukkit.plugin.java.JavaPlugin;

public final class StatusSignsPlugin extends JavaPlugin {

    private StatusSigns signs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = YamlFile.loadWithDefaults(getDataPath().resolve("config.yml"), () -> getResource("config.yml"));
        var ping = YamlFile.section(config, "ping");
        long intervalTicks = ((Number) ping.getOrDefault("interval-ticks", 50)).longValue();
        int timeoutMillis = ((Number) ping.getOrDefault("timeout-millis", 1500)).intValue();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"));

        signs = new StatusSigns(this,
                new SignStore(getDataPath().resolve("signs.yml"), getLogger()),
                new StatusPinger(timeoutMillis),
                new SignRenderer(messages));
        getLogger().info(signs.load() + " signs loaded");
        signs.start(intervalTicks);

        getServer().getPluginManager().registerEvents(new SignClickListener(this, signs, messages), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(CreateSignCommand.create(signs, messages), "Erstellt ein Status-Schild"));
    }

    @Override
    public void onDisable() {
        if (signs != null) {
            signs.stop();
        }
    }
}
