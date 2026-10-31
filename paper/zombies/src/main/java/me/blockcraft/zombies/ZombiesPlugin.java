package me.blockcraft.zombies;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.LobbyProtection;
import me.blockcraft.api.game.ServerRestart;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.game.StatsCommand;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopCommand;
import me.blockcraft.api.game.TopList;
import me.blockcraft.common.message.Messages;
import me.blockcraft.zombies.arena.ArenaMap;
import me.blockcraft.zombies.config.ZombiesConfig;
import me.blockcraft.zombies.game.ZombiesGame;
import me.blockcraft.zombies.menu.GuideDialog;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;

public final class ZombiesPlugin extends JavaPlugin {

    private ZombiesGame game;

    @Override
    public void onEnable() {
        try {
            enable();
        } catch (RuntimeException exception) {
            getSLF4JLogger().error("Zombies could not start; stopping the game server", exception);
            getServer().shutdown();
        }
    }

    private void enable() {
        saveDefaultConfig();
        var api = BlockcraftApi.get();
        var config = ZombiesConfig.from(getConfig());
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags()).with(
                net.kyori.adventure.text.minimessage.tag.resolver.Formatter.number("max-waves", config.waves().total()),
                net.kyori.adventure.text.minimessage.tag.resolver.Formatter.number("heal-delay", config.healthRegenTicks() / 20));
        var gameConfig = GameConfig.from(getConfig(), 1);
        var arenaFile = api.map().file(this, "arena.yml");
        if (Files.notExists(arenaFile)) {
            try (var bundled = getResource("arena.yml")) {
                if (bundled == null) {
                    throw new IllegalStateException("Missing bundled Zombies arena");
                }
                Files.copy(bundled, arenaFile);
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not prepare Zombies arena", exception);
            }
        }
        var arena = new ArenaMap(arenaFile, Bukkit.getWorlds().getFirst());
        if (!arena.prepare()) {
            throw new IllegalStateException("Zombies arena is not ready. Starter generation needs an empty region; configure " + arenaFile);
        }
        arena.keepLoaded(this);
        var stats = new StatsRepository(api.database(), "bc_zombies_stats");
        stats.createTable();
        var top = new TopList(this, stats, messages);
        game = new ZombiesGame(this, gameConfig, config, messages, arena, stats,
                StatsMenus.of(messages, stats, top, gameConfig.globalStatsHead()));
        api.presence().maxPlayers(config.maxPlayers());
        List.of(new GameListener(this, game), new AbilityListener(game), game.lobbyHotbar(), new ArenaProtection(() -> false),
                new LobbyProtection(() -> !game.running(), api.buildMode(), () -> java.util.Optional.of(arena.point("lobby"))))
                .forEach(listener -> getServer().getPluginManager().registerEvents(listener, this));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(StartCommand.of(game.lobbyCountdown()).build());
            commands.register(StatsCommand.create(this, stats, messages));
            commands.register(TopCommand.create(top));
            var setup = SetupCommand.root().executes(context -> {
                context.getSource().getSender().sendMessage(messages.get("setup.help"));
                return Command.SINGLE_SUCCESS;
            });
            for (String point : List.of("lobby", "spawn", "spectator")) {
                setup.then(Commands.literal(point).executes(context -> {
                    var player = context.getSource().getPlayerOrThrow();
                    if (!game.isLobby() || !player.getWorld().equals(arena.world())) {
                        player.sendMessage(messages.get("setup.lobby-only"));
                        return 0;
                    }
                    arena.setPoint(point, player.getLocation());
                    player.sendMessage(messages.get("setup.saved", Placeholder.unparsed("point", point)));
                    return Command.SINGLE_SUCCESS;
                }));
            }
            setup.then(Commands.literal("check").executes(context -> {
                boolean ready = arena.ready();
                context.getSource().getSender().sendMessage(messages.get(ready ? "setup.ready" : "setup.invalid"));
                return ready ? Command.SINGLE_SUCCESS : 0;
            }));
            commands.register(setup.build());
            commands.register(Commands.literal("zombies").requires(source -> source.getSender() instanceof Player)
                    .executes(context -> {
                        new GuideDialog(messages).show(context.getSource().getPlayerOrThrow());
                        return Command.SINGLE_SUCCESS;
                    }).build());
        });
        ServerRestart.whenIdle(this, gameConfig);
        Bukkit.getOnlinePlayers().forEach(game::join);
    }

    @Override
    public void onDisable() {
        if (game != null) {
            game.stop();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }
}
