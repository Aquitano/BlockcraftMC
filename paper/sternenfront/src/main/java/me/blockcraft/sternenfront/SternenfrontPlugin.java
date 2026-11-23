package me.blockcraft.sternenfront;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.ForceMapCommand;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.RoundHost;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.world.WaitingLobby;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.sternenfront.appearance.BodyRigs;
import me.blockcraft.sternenfront.appearance.TargetOutlines;
import me.blockcraft.sternenfront.arena.FrontChoice;
import me.blockcraft.sternenfront.arena.FrontMaps;
import me.blockcraft.sternenfront.config.FeelConfig;
import me.blockcraft.sternenfront.config.SternenfrontConfig;
import me.blockcraft.sternenfront.game.BattleIntro;
import me.blockcraft.sternenfront.game.Career;
import me.blockcraft.sternenfront.game.Faction;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.debug.BotDebug;
import me.blockcraft.sternenfront.hero.HeroConfig;
import me.blockcraft.sternenfront.listener.ArenaListener;
import me.blockcraft.sternenfront.listener.ChatListener;
import me.blockcraft.sternenfront.listener.CombatListener;
import me.blockcraft.sternenfront.secret.Secrets;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Optional;

public final class SternenfrontPlugin extends JavaPlugin {

    private RoundHost<SternenfrontGame> host;
    private BotDebug botDebug;

    @Override
    public void onEnable() {
        BundledDefaults.apply(this);
        var read = BundledDefaults.strict(getConfig());
        var gameConfig = GameConfig.from(getConfig(), read.flag("bots.enabled") ? 1 : 2);
        var config = SternenfrontConfig.from(getConfig());
        var heroConfig = HeroConfig.from(getConfig());
        var feel = FeelConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "bc_sternenfront_stats");
        var messages = services.messages();
        var maps = new FrontMaps(this, messages);
        if (!maps.copied()) {
            getSLF4JLogger().warn("No Sternenfront map template to copy; every round plays the main world and restarts the server");
        }
        var lobby = api.waitingLobby().map(WaitingLobby::spawn).orElse(maps.main().lobbySpawn());
        lobby.getWorld().setSpawnLocation(lobby);
        int voteClosesAt = read.integer("countdown.vote-closes-at");
        var career = new Career(this, messages, api.database(), getConfig().getLongList("career.ranks"));
        Tasks.async(this, career::createTable);
        api.presence().maxPlayers(config.capacity());
        var riggedSides = EnumSet.noneOf(Faction.class);
        if (read.flag("appearance.droids")) {
            riggedSides.add(Faction.HEGEMONIE);
        }
        if (read.flag("appearance.infantry")) {
            riggedSides.add(Faction.KOLONIEN);
        }
        boolean outlines = read.flag("outlines.enabled");
        botDebug = new BotDebug(this, messages, () -> host.current());

        host = RoundHost.start(this, gameConfig, round -> {
            var game = new SternenfrontGame(round, gameConfig, config, heroConfig, feel, messages, new FrontChoice(maps, round),
                    voteClosesAt, lobby, career, services.stats(), services.menus());
            var bodyRigs = riggedSides.isEmpty() ? Optional.<BodyRigs>empty() : Optional.of(BodyRigs.start(round, game, riggedSides));
            bodyRigs.ifPresent(rigs -> {
                game.rigs(rigs);
                round.onClose(rigs::close);
            });
            var secrets = Secrets.start(round, game, messages);
            round.onClose(secrets::close);
            if (outlines) {
                var targetOutlines = new TargetOutlines(round, game, bodyRigs);
                round.onClose(targetOutlines::close);
            }
            round.listen(
                    game.hotbar(),
                    new RoundConnections(game),
                    WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode())
                            .rescue(() -> Optional.of(lobby)),
                    new ArenaProtection(() -> false),
                    new ArenaListener(game, api.buildMode()),
                    new CombatListener(game, api.buildMode(), botDebug, secrets),
                    new ChatListener(game, messages),
                    game.fieldObjectives());
            return game;
        });

        services.commands(commands -> {
            commands.register(StartCommand.of(host).build());
            commands.register(ForceMapCommand.create(messages, maps.vote()::keys,
                    () -> host.current().phase() == GamePhase.LOBBY && maps.vote().open(), maps.vote()::force));
            commands.register(botDebug.command());
            commands.register(BattleIntro.command(() -> host.current().intro()));
        });

        Bukkit.getOnlinePlayers().forEach(host.current()::join);
    }

    @Override
    public void onDisable() {
        if (host != null) {
            host.close();
        }
        if (botDebug != null) {
            botDebug.close();
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        Bukkit.getScheduler().cancelTasks(this);
    }
}
