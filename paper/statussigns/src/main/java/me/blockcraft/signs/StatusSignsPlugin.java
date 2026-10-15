package me.blockcraft.signs;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.common.message.Messages;
import me.blockcraft.signs.command.CreateSignCommand;
import me.blockcraft.signs.listener.SignClickListener;
import me.blockcraft.signs.sign.SignRenderer;
import me.blockcraft.signs.sign.SignStore;
import me.blockcraft.signs.sign.StatusSigns;
import org.bukkit.plugin.java.JavaPlugin;

public final class StatusSignsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), BlockcraftApi.get().tags());

        var signs = new StatusSigns(this,
                new SignStore(BlockcraftApi.get().map().file(this, "signs.yml"), getLogger()),
                BlockcraftApi.get().groups(),
                new SignRenderer(messages));
        getLogger().info(signs.load() + " signs loaded");
        signs.start();

        getServer().getPluginManager().registerEvents(new SignClickListener(this, signs), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                new CommandRegistry(event.registrar(), messages).register(CreateSignCommand.create(signs, messages)));
    }
}
