package me.blockcraft.sternenfront;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.ArenaProtection;
import me.blockcraft.api.game.ForceMapCommand;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameServices;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.StartCommand;
import me.blockcraft.api.world.WaitingLobby;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.sternenfront.appearance.BodyRigs;
import me.blockcraft.sternenfront.appearance.TargetOutlines;
import me.blockcraft.sternenfront.arena.FrontChoice;
import me.blockcraft.sternenfront.config.FeelConfig;
import me.blockcraft.sternenfront.config.SternenfrontConfig;
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

    private SternenfrontGame game;
    private BodyRigs bodyRigs;
    private TargetOutlines targetOutlines;
    private BotDebug botDebug;
    private Secrets secrets;

    @Override
    public void onEnable() {
        BundledDefaults.apply(this);
        var read = BundledDefaults.strict(getConfig());
        var gameConfig = GameConfig.from(getConfig(), read.flag("bots.enabled") ? 1 : 2);
        var config = SternenfrontConfig.from(getConfig());
        var api = BlockcraftApi.get();
        var services = GameServices.load(this, gameConfig, "bc_sternenfront_stats");
        var messages = services.messages();
        var maps = new FrontChoice(this, messages);
        var lobby = api.waitingLobby().map(WaitingLobby::spawn).orElse(maps.main().lobbySpawn());
        lobby.getWorld().setSpawnLocation(lobby);
        game = new SternenfrontGame(this, gameConfig, config, HeroConfig.from(getConfig()), FeelConfig.from(getConfig()),
                messages, maps, read.integer("countdown.vote-closes-at"), lobby, services.stats(), services.menus());
        api.presence().maxPlayers(config.capacity());
        var riggedSides = EnumSet.noneOf(Faction.class);
        if (read.flag("appearance.droids")) {
            riggedSides.add(Faction.HEGEMONIE);
        }
        if (read.flag("appearance.infantry")) {
            riggedSides.add(Faction.KOLONIEN);
        }
        var outlines = TargetOutlines.Rule.from(game);
        if (!riggedSides.isEmpty()) {
            bodyRigs = BodyRigs.start(this, game, riggedSides, outlines);
            game.rigs(bodyRigs);
        }
        secrets = Secrets.start(this, game, messages);
        if (read.flag("outlines.enabled")) {
            targetOutlines = new TargetOutlines(this, game, Optional.ofNullable(bodyRigs), outlines);
        }
        botDebug = new BotDebug(game, bodyRigs);

        services.listen(
                game.hotbar(),
                new RoundConnections(game),
                WorldProtection.during(() -> game.phase() != GamePhase.INGAME, api.buildMode())
                        .rescue(() -> Optional.of(lobby)),
                new ArenaProtection(() -> false),
                new ArenaListener(game, api.buildMode()),
                new CombatListener(game, api.buildMode(), botDebug, secrets),
                new ChatListener(game, messages));
        services.listen(game.fieldObjectives());

        services.commands(commands -> {
            commands.register(StartCommand.of(game.countdown()).build());
            commands.register(ForceMapCommand.create(messages, maps.vote()::keys,
                    () -> game.phase() == GamePhase.LOBBY && maps.vote().open(), maps.vote()::force));
            commands.register(botDebug.command());
            commands.register(game.intro().command());
        });

        Bukkit.getOnlinePlayers().forEach(game::join);
    }

    @Override
    public void onDisable() {
        if (secrets != null) {
            secrets.close();
        }
        if (targetOutlines != null) {
            targetOutlines.close();
        }
        if (botDebug != null) {
            botDebug.close();
        }
        if (bodyRigs != null) {
            bodyRigs.close();
        }
        if (game != null) {
            game.stop();
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.kick(Component.empty()));
        Bukkit.getScheduler().cancelTasks(this);
    }
}
