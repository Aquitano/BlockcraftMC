package me.blockcraft.lobby;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.ThrowFight;
import me.blockcraft.api.world.WorldProtection;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.activity.Ambience;
import me.blockcraft.lobby.activity.Flights;
import me.blockcraft.lobby.activity.Herobrine;
import me.blockcraft.lobby.activity.LobbyNews;
import me.blockcraft.lobby.activity.LobbyQuirks;
import me.blockcraft.lobby.activity.LobbyThrows;
import me.blockcraft.lobby.activity.LoreNotes;
import me.blockcraft.lobby.activity.MapSecrets;
import me.blockcraft.lobby.activity.NoticeBoards;
import me.blockcraft.lobby.activity.Parkour;
import me.blockcraft.lobby.command.ParkourCommand;
import me.blockcraft.lobby.activity.PondFishing;
import me.blockcraft.lobby.activity.Prisoner;
import me.blockcraft.lobby.activity.Props;
import me.blockcraft.lobby.activity.QuestGivers;
import me.blockcraft.lobby.activity.SantaSleigh;
import me.blockcraft.lobby.activity.SeasonalAtmosphere;
import me.blockcraft.lobby.activity.SeasonalDecor;
import me.blockcraft.lobby.activity.SeasonalEvents;
import me.blockcraft.lobby.activity.SeasonalScenes;
import me.blockcraft.lobby.activity.ShootingStars;
import me.blockcraft.api.season.Snowfall;
import me.blockcraft.lobby.command.ClearChatCommand;
import me.blockcraft.lobby.command.DailyCommand;
import me.blockcraft.lobby.command.FlightCommand;
import me.blockcraft.lobby.command.LobbySetCommand;
import me.blockcraft.lobby.command.NavigatorCommand;
import me.blockcraft.lobby.command.SetHoloCommand;
import me.blockcraft.lobby.command.SetSpawnCommand;
import me.blockcraft.lobby.command.SetPropCommand;
import me.blockcraft.lobby.command.SleighCommand;
import me.blockcraft.lobby.command.TeamBoardCommand;
import me.blockcraft.lobby.config.ActivitiesConfig;
import me.blockcraft.lobby.config.FlightsConfig;
import me.blockcraft.lobby.config.GameNpcConfig;
import me.blockcraft.lobby.config.HerobrineConfig;
import me.blockcraft.lobby.config.LobbyConfig;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.config.LoreNoteConfig;
import me.blockcraft.lobby.config.Quest;
import me.blockcraft.lobby.config.PropsConfig;
import me.blockcraft.lobby.config.ScenesConfig;
import me.blockcraft.lobby.config.SeasonTheme;
import me.blockcraft.lobby.config.SleighConfig;
import me.blockcraft.lobby.config.ShootingStarConfig;
import me.blockcraft.lobby.cosmetic.CosmeticPreviews;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Secrets;
import me.blockcraft.lobby.cosmetic.Trails;
import me.blockcraft.lobby.cookies.CookieBlock;
import me.blockcraft.lobby.cookies.CookieClicker;
import me.blockcraft.lobby.cookies.CookieProgress;
import me.blockcraft.lobby.daily.DailyCourier;
import me.blockcraft.lobby.daily.DailyRewards;
import me.blockcraft.lobby.friends.FriendsChannel;
import me.blockcraft.lobby.gadget.Gadgets;
import me.blockcraft.lobby.hider.PlayerHider;
import me.blockcraft.lobby.item.LobbyItems;
import me.blockcraft.lobby.konami.KonamiListener;
import me.blockcraft.lobby.listener.CosmeticSettingsListener;
import me.blockcraft.lobby.listener.DeathListener;
import me.blockcraft.lobby.listener.DoubleJumpListener;
import me.blockcraft.lobby.listener.GadgetListener;
import me.blockcraft.lobby.listener.InventoryLockListener;
import me.blockcraft.lobby.listener.JoinQuitListener;
import me.blockcraft.lobby.listener.LobbyHud;
import me.blockcraft.lobby.listener.LobbyItemListener;
import me.blockcraft.lobby.listener.MoveListener;
import me.blockcraft.lobby.listener.PetListener;
import me.blockcraft.lobby.listener.ProtectionListener;
import me.blockcraft.lobby.listener.SecretsListener;
import me.blockcraft.lobby.listener.ShopNpcListener;
import me.blockcraft.lobby.listener.WorldDownloaderListener;
import me.blockcraft.lobby.menu.Menus;
import me.blockcraft.lobby.menu.NavigatorTeleports;
import me.blockcraft.lobby.menu.SecretsSection;
import me.blockcraft.lobby.npc.GameNpcs;
import me.blockcraft.lobby.npc.Holograms;
import me.blockcraft.lobby.npc.Leaderboards;
import me.blockcraft.lobby.npc.OwnPlacings;
import me.blockcraft.lobby.npc.Podiums;
import me.blockcraft.lobby.npc.ShopNpc;
import me.blockcraft.lobby.rank.PremiumRank;
import me.blockcraft.lobby.recap.RoundRecapListener;
import me.blockcraft.lobby.team.TeamBoard;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class LobbyPlugin extends JavaPlugin {

    private final List<AutoCloseable> closables = new ArrayList<>();

    @Override
    public void onEnable() {
        var yaml = YamlFile.loadWithDefaults(getDataPath().resolve("config.yml"), () -> getResource("config.yml"));
        var config = LobbyConfig.fromYaml(yaml);
        var api = BlockcraftApi.get();
        var activitySettings = new LinkedHashMap<>(YamlFile.section(yaml, "activities"));
        activitySettings.putAll(YamlConfiguration.loadConfiguration(api.map().file(this, "activity-settings.yml").toFile()).getValues(false));
        var activities = ActivitiesConfig.fromYaml(Map.of("activities", activitySettings));
        var profiles = api.profiles();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var locations = Locations.load(api.map().file(this, "locations.yml"), LobbyLocation::key);
        var coins = api.coins();
        var luckPerms = LuckPermsProvider.get();

        var trails = new Trails(config.trailIntervalTicks(), config.trailViewDistance());
        var pets = new Pets(messages);
        var hider = new PlayerHider(this, api.settings(), config.hiderCooldownMillis());
        var activityLocations = Locations.load(api.map().file(this, "activities.yml"));
        var parkour = new Parkour(this, activityLocations, activities, messages);
        closables.add(parkour);
        var throwFight = new LobbyThrows(this, messages, api.calendar(), ThrowFight.Settings.fromYaml(YamlFile.section(yaml, "throw-fight")),
                parkour::running);
        closables.add(throwFight);
        var items = new LobbyItems(messages, throwFight::item);
        var secrets = new Secrets(this, messages, api.unlocks(), coins, config.secretReward());
        var chunks = new ChunkTickets(this);
        var spotEntities = new SpotEntities(chunks);
        register(spotEntities);
        var quirks = new LobbyQuirks(this, activities, messages, pets, secrets);
        var prisoner = new Prisoner(spotEntities, activityLocations, messages);
        var mapSecrets = new MapSecrets(this, activityLocations, messages, secrets, activities.placeRadius());
        var herobrine = new Herobrine(this, HerobrineConfig.fromYaml(YamlFile.section(yaml, "herobrine")), secrets);
        var stars = new ShootingStars(this, ShootingStarConfig.fromYaml(YamlFile.section(yaml, "shooting-stars")), messages, secrets);
        closables.addAll(List.of(quirks, prisoner, mapSecrets, herobrine, stars));
        var themes = SeasonTheme.all(YamlFile.section(yaml, "seasons"), getSLF4JLogger());
        var snowfall = new Snowfall(this, () -> locations.get(LobbyLocation.SPAWN));
        closables.add(snowfall);
        var decor = new SeasonalDecor(api.calendar(), themes, snowfall);
        var atmosphere = new SeasonalAtmosphere(this, api.calendar(), themes);
        var sleigh = new SantaSleigh(this, SleighConfig.fromYaml(YamlFile.section(yaml, "sleigh")), messages, api.calendar());
        closables.addAll(List.of(atmosphere, sleigh));
        var pieces = PropsConfig.fromYaml(YamlFile.section(yaml, "props"), getSLF4JLogger());
        var props = new Props(this, chunks, pieces, Locations.load(api.map().file(this, "props.yml")));
        props.start();
        closables.add(props);
        var flights = new Flights(this, FlightsConfig.fromYaml(YamlFile.section(yaml, "flights")), pieces,
                () -> locations.get(LobbyLocation.SPAWN));
        closables.add(flights);
        var seasons = new SeasonalEvents(this, spotEntities, activityLocations, activities, messages,
                () -> locations.get(LobbyLocation.SPAWN).ifPresent(spawn -> config.fireworkSpots()
                        .forEach(spot -> MoveListener.launchFirework(spot.toLocation(spawn.getWorld())))), decor);
        closables.add(seasons);
        profiles.advent(player -> seasons.openAdvent(player, back -> profiles.open(back, Optional.empty())));
        closables.add(() -> {
            profiles.clearRecentRound();
            profiles.clearAdvent();
            profiles.clearShop();
        });
        var gadgets = new Gadgets(this, messages, Duration.ofSeconds(config.gadgetCooldownSeconds()));
        closables.add(gadgets::restorePaint);
        var friends = new FriendsChannel(this, config.friendsTimeoutTicks());
        closables.add(friends::close);
        var previews = new CosmeticPreviews(this, messages, config.cosmeticPreviewTicks());
        closables.add(previews::close);
        var loreNotes = new LoreNotes(this, spotEntities, activityLocations, LoreNoteConfig.fromYaml(YamlFile.section(yaml, "lore-notes")), messages);
        var cookies = new CookieClicker(this, api.database(), messages,
                CookieProgress.Rules.fromYaml(YamlFile.section(yaml, "cookies")));
        closables.add(cookies);
        var cookieBlock = new CookieBlock(activityLocations, cookies, messages, chunks);
        closables.add(cookieBlock);
        register(cookies, cookieBlock);
        var menus = new Menus(this, messages, config, locations, api.unlocks(), api.settings(), api.messages(),
                api.servers(), api.server(), new PremiumRank(this, luckPerms), pets, api.groups(), friends, previews,
                new NavigatorTeleports());
        var achievements = api.achievements();
        achievements.section(new SecretsSection(messages, loreNotes, config.secretRiddlesAfter()));
        closables.add(achievements::clearSection);
        var shopNpc = new ShopNpc(this, locations, messages, quirks::owns);
        shopNpc.start(config.shopNpcRespawnSeconds());
        closables.add(shopNpc::stop);
        var leaderboards = new Leaderboards(this, api.database(), messages, api.messages(), config.leaderboards());
        var holograms = new Holograms(this, messages, leaderboards);
        var podiums = new Podiums(spotEntities, activityLocations, leaderboards, messages);
        holograms.afterRefresh(podiums::refresh);
        holograms.start(config.leaderboardRefreshSeconds() * 20L);
        closables.add(holograms::stop);
        var ownRadius = YamlFile.section(yaml, "leaderboards").get("own-radius") instanceof Number radius ? radius.doubleValue() : 10.0;
        var ownPlacings = new OwnPlacings(this, leaderboards, messages, Math.clamp(ownRadius, 2.0, 48.0),
                Duration.ofSeconds(config.leaderboardRefreshSeconds()));
        var gameNpcs = new GameNpcs(this, spotEntities, activityLocations, GameNpcConfig.fromYaml(YamlFile.section(yaml, "game-npcs")),
                config.navigatorGroups(), api.groups(), messages, api.messages());
        var boardSettings = YamlFile.section(yaml, "notice-boards");
        var noticeBoards = new NoticeBoards(this, spotEntities, activityLocations, messages,
                String.valueOf(boardSettings.getOrDefault("changelog-version", "1")),
                String.valueOf(boardSettings.getOrDefault("rules-command", "regeln")));
        var quests = new QuestGivers(spotEntities, activityLocations, Quest.fromYaml(YamlFile.section(yaml, "quests")), messages);
        var daily = new DailyCourier(this, spotEntities, activityLocations, DailyRewards.fromYaml(YamlFile.section(yaml, "daily-rewards")), messages);
        closables.addAll(List.of(gameNpcs, podiums, ownPlacings, loreNotes, noticeBoards, quests, daily));
        register(gameNpcs, ownPlacings, loreNotes, noticeBoards, daily);
        LobbyNews.start(this, messages, YamlFile.section(yaml, "news")).ifPresent(news -> {
            closables.add(news);
            register(news);
        });
        var ambience = new Ambience(this, activityLocations, YamlFile.section(yaml, "ambient"), gameNpcs::places,
                YamlFile.section(yaml, "ambience"));
        var scenes = new SeasonalScenes(this, chunks, api.calendar(), activityLocations, ScenesConfig.fromYaml(YamlFile.section(yaml, "scenes")));
        closables.addAll(List.of(ambience, scenes));
        register(scenes);
        var optionalSpots = new ArrayList<>(GameNpcs.spots());
        leaderboards.names().stream().map(Podiums::spot).sorted().forEach(optionalSpots::add);
        optionalSpots.addAll(quests.spots());
        optionalSpots.add(DailyCourier.SPOT);
        optionalSpots.addAll(scenes.spots());
        optionalSpots.addAll(ambience.spots());
        var optionalBlocks = new ArrayList<>(loreNotes.spots());
        optionalBlocks.addAll(NoticeBoards.SPOTS);
        optionalBlocks.add(CookieBlock.SPOT);

        var messenger = getServer().getMessenger();
        messenger.registerIncomingPluginChannel(this, WorldDownloaderListener.INIT_CHANNEL, new WorldDownloaderListener(messages));

        var hud = new LobbyHud(this, messages);
        hud.start(config.hudRefreshSeconds() * 20L);
        closables.add(hud::stop);
        register(previews, hud, parkour, quirks, seasons, decor, snowfall, sleigh, throwFight, mapSecrets, stars.wishes(),
                new PondFishing(activityLocations, activities, messages, secrets, throwFight::item),
                new JoinQuitListener(messages, locations, items, trails, pets, hider),
                new CosmeticSettingsListener(messages, trails, pets, gadgets),
                new RoundRecapListener(this, messages, config),
                new LobbyItemListener(messages, menus, hider),
                new ProtectionListener(),
                WorldProtection.during(() -> true, api.buildMode()).withHeldItems(),
                new SecretsListener(messages, secrets),
                new ShopNpcListener(messages, menus, hud, config),
                new KonamiListener(this, messages, api.unlocks(), secrets, activities.konamiGap()),
                new PetListener(pets, messages),
                new MoveListener(messages, config, locations, trails, activities.fireworkCooldown(), parkour),
                new DoubleJumpListener(config, parkour),
                new GadgetListener(gadgets, parkour),
                new InventoryLockListener(),
                new DeathListener(locations, items, hider));
        api.presence().maxPlayers(config.maxPlayers());

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(SetSpawnCommand.create(locations, messages));
            commands.register(SetHoloCommand.create(messages,
                    Stream.concat(config.holograms().stream(), leaderboards.names().stream()).toList(),
                    holograms, locations, shopNpc));
            commands.register(ClearChatCommand.create(messages));
            commands.register(ParkourCommand.create(parkour));
            commands.register(DailyCommand.create(daily));
            commands.register(SleighCommand.create(sleigh, messages));
            commands.register(FlightCommand.create(flights, messages));
            commands.register(SetPropCommand.create(messages, props));
            commands.register(NavigatorCommand.create(menus), "nav", "spiele");
            var activeTeamBoard = api.map().file(this, "team-board.yml");
            var teamBoardSource = api.map().template()
                    .map(template -> template.directory().resolve("blockcraft").resolve(getName()).resolve("team-board.yml"))
                    .orElse(activeTeamBoard);
            commands.register(TeamBoardCommand.create(new TeamBoard(teamBoardSource, activeTeamBoard, messages), messages));
            commands.register(LobbySetCommand.create(activityLocations, messages, activities, locations,
                    List.copyOf(optionalSpots), List.copyOf(optionalBlocks), () -> {
                        parkour.refresh();
                        seasons.refresh();
                        prisoner.refresh();
                        gameNpcs.refresh();
                        podiums.placed();
                        loreNotes.refresh();
                        noticeBoards.refresh();
                        quests.refresh();
                        daily.refresh();
                        cookieBlock.refresh();
                        scenes.refresh();
                    }));
        });
    }

    @Override
    public void onDisable() {
        for (var closing : closables.reversed()) {
            try {
                closing.close();
            } catch (Exception e) {
                getSLF4JLogger().warn("Could not close {}", closing.getClass().getSimpleName(), e);
            }
        }
        closables.clear();
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}
