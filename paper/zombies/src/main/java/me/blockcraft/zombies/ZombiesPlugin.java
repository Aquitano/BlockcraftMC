package me.blockcraft.zombies;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.ForceMapCommand;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.RoundHost;
import me.blockcraft.api.game.SetupCommand;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.zombies.arena.ArenaChoice;
import me.blockcraft.zombies.arena.ArenaMap;
import me.blockcraft.zombies.arena.ArenaMaps;
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
import java.util.Optional;

public final class ZombiesPlugin extends JavaPlugin {

    private RoundHost<ZombiesGame> host;

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
        BundledDefaults.apply(this);
        var api = BlockcraftApi.get();
        var config = ZombiesConfig.from(getConfig());
        var gameConfig = GameConfig.from(getConfig(), 1);
        var arenaFile = api.map().file(this, ArenaMaps.FILE);
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
        var services = GameServices.load(this, gameConfig, "bc_zombies_stats");
        var messages = services.messages().with(
                Formatter.number("max-waves", config.waves().total()),
                Formatter.number("heal-delay", config.healthRegenTicks() / 20));
        var maps = new ArenaMaps(this, messages, arena);
        int voteClosesAt = BundledDefaults.strict(getConfig()).integer("countdown.vote-closes-at");
        api.presence().maxPlayers(config.maxPlayers());
        if (!maps.copied()) {
            getSLF4JLogger().warn("No Zombies map template to copy; every round plays the main world and restarts the server");
        }
        host = RoundHost.start(this, gameConfig, round -> {
            var game = new ZombiesGame(round, gameConfig, config, messages, new ArenaChoice(maps, round), voteClosesAt,
                    services.stats(), services.menus());
            round.listen(new RoundConnections(game), new GameListener(round, game), new AbilityListener(game), game.lobbyHotbar(),
                    new ArenaProtection(() -> false),
                    WorldProtection.during(() -> !game.running(), api.buildMode()).rescue(() -> Optional.of(game.lobbySpawn())));
            return game;
        });
        services.commands(commands -> {
            commands.register(StartCommand.of(host).build());
            commands.register(ForceMapCommand.create(messages, maps.vote()::keys, () -> host.current().isLobby() && maps.vote().open(),
                    maps.vote()::force));
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
                    if (!host.current().isLobby() || !player.getWorld().equals(arena.world())) {
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
        Bukkit.getOnlinePlayers().forEach(host.current()::join);
    }

    @Override
    public void onDisable() {
        if (host != null) {
            host.close();
        }
        Bukkit.getScheduler().cancelTasks(this);
    }
}
