package me.blockcraft.archive;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.LobbyProtection;
import me.blockcraft.archive.command.ArchiveSetup;
import me.blockcraft.archive.command.NoteCommand;
import me.blockcraft.archive.exhibit.ExhibitPool;
import me.blockcraft.archive.exhibit.ExhibitWorlds;
import me.blockcraft.archive.hall.EntranceHall;
import me.blockcraft.archive.menu.ArchiveMenus;
import me.blockcraft.archive.note.NoteRepository;
import me.blockcraft.common.message.Messages;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * The Zeitkapsel: old maps of earlier servers to walk and fly through, checkpoints to jump between and notes staff
 * left for later visitors. There is nothing to win; the whole server keeps the rules of a game's waiting lobby.
 */
public final class ArchivePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = ArchiveConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var notes = new NoteRepository(api.database());
        notes.createTable();

        var pool = new ExhibitPool(api.map().file(this, "exhibits"), getLogger());
        var worlds = new ExhibitWorlds(this, pool, notes, messages, config.unloadAfter());
        var hall = EntranceHall.build(messages.get("hall.welcome"));
        var menus = new ArchiveMenus(messages, pool, worlds, notes, hall);
        var hotbar = new ArchiveHotbar(this, messages, menus);
        api.presence().maxPlayers(config.maxPlayers());

        register(hotbar);
        register(new VisitorListener(messages, hall, worlds, hotbar));
        // Falling is the visitor listener's: below an exhibit it leads back to the exhibit's arrival, not the hall.
        register(new LobbyProtection(() -> true, api.buildMode(), Optional::empty));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(ArchiveSetup.create(pool, worlds, messages));
            commands.register(NoteCommand.create(messages, worlds, notes, config.noteMaxLength()));
        });

        worlds.start();
    }

    private void register(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }
}
