package me.blockcraft.zombies;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.zombies.arena.ArenaMap;
import me.blockcraft.zombies.config.ZombiesConfig;
import me.blockcraft.zombies.game.ZombiesGame;
import me.blockcraft.zombies.menu.GuideDialog;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
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
        getConfig().options().copyDefaults(true);
        var api = BlockcraftApi.get();
        var config = ZombiesConfig.from(getConfig());
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
        var services = GameServices.load(this, gameConfig, "bc_zombies_stats");
        var messages = services.messages().with(
                Formatter.number("max-waves", config.waves().total()),
                Formatter.number("heal-delay", config.healthRegenTicks() / 20));
        game = new ZombiesGame(this, gameConfig, config, messages, arena, services.stats(), services.menus());
        api.presence().maxPlayers(config.maxPlayers());
        services.listen(new RoundConnections(game), new GameListener(this, game), new AbilityListener(game), game.lobbyHotbar(), new ArenaProtection(() -> false),
                WorldProtection.during(() -> !game.running(), api.buildMode()).rescue(() -> java.util.Optional.of(game.lobbySpawn())));
        services.commands(commands -> {
            commands.register(StartCommand.of(game.lobbyCountdown()).build());
            var setup = SetupCommand.root().executes(context -> {
                context.getSource().getSender().sendMessage(messages.get("setup.help"));
                return Command.SINGLE_SUCCESS;
            });
            for (String point : List.of("lobby", "spawn", "spectator")) {
                if (point.equals("lobby") && api.waitingLobby().isPresent()) {
                    continue;
                }
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
