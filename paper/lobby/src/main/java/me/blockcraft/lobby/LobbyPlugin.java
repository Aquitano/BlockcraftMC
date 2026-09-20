package me.blockcraft.lobby;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.menu.Profiles;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.config.Locations;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.ProxyChannel;
import me.blockcraft.lobby.command.ClearChatCommand;
import me.blockcraft.lobby.command.SetHoloCommand;
import me.blockcraft.lobby.command.SetSpawnCommand;
import me.blockcraft.lobby.config.LobbyConfig;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.Head;
import me.blockcraft.lobby.cosmetic.CosmeticPreviews;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.cosmetic.Shirt;
import me.blockcraft.lobby.cosmetic.Trails;
import me.blockcraft.lobby.friends.FriendsChannel;
import me.blockcraft.lobby.gadget.Gadgets;
import me.blockcraft.lobby.hider.PlayerHider;
import me.blockcraft.lobby.item.LobbyItems;
import me.blockcraft.lobby.listener.CommandBlockListener;
import me.blockcraft.lobby.listener.CosmeticSettingsListener;
import me.blockcraft.lobby.listener.DeathListener;
import me.blockcraft.lobby.listener.DoubleJumpListener;
import me.blockcraft.lobby.listener.GadgetListener;
import me.blockcraft.lobby.listener.InventoryLockListener;
import me.blockcraft.lobby.listener.JoinQuitListener;
import me.blockcraft.lobby.listener.LobbyItemListener;
import me.blockcraft.lobby.listener.LobbyHud;
import me.blockcraft.lobby.listener.MoveListener;
import me.blockcraft.lobby.listener.PetListener;
import me.blockcraft.lobby.listener.ProtectionListener;
import me.blockcraft.lobby.listener.SecretsListener;
import me.blockcraft.lobby.listener.ShopNpcListener;
import me.blockcraft.lobby.listener.WorldDownloaderListener;
import me.blockcraft.lobby.menu.Menus;
import me.blockcraft.lobby.menu.ShopMenu;
import me.blockcraft.lobby.network.GroupCounts;
import me.blockcraft.lobby.npc.Holograms;
import me.blockcraft.lobby.npc.Leaderboards;
import me.blockcraft.lobby.npc.ShopNpc;
import me.blockcraft.lobby.recap.RoundRecapListener;
import me.blockcraft.lobby.rank.PremiumRank;
import me.blockcraft.lobby.storage.LegacyUnlocks;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;

public final class LobbyPlugin extends JavaPlugin {

    private static final long GROUP_COUNTS_INTERVAL_TICKS = 100L;

    private Profiles profiles;
    private CosmeticPreviews previews;
    private ShopNpc shopNpc;
    private GroupCounts groupCounts;
    private FriendsChannel friends;
    private Gadgets gadgets;
    private LobbyHud hud;
    private Holograms holograms;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = LobbyConfig.fromYaml(YamlFile.loadWithDefaults(getDataPath().resolve("config.yml"), () -> getResource("config.yml")));
        var api = BlockcraftApi.get();
        profiles = api.profiles();
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), api.tags());
        var locations = Locations.load(api.map().file(this, "locations.yml"), LobbyLocation::key);
        var coins = api.coins();
        var luckPerms = LuckPermsProvider.get();

        var trails = new Trails(config.trailIntervalTicks(), config.trailViewDistance());
        var pets = new Pets(messages);
        var hider = new PlayerHider(this, api.settings(), config.hiderCooldownMillis());
        var items = new LobbyItems(messages);
        importLegacyUnlocks(new LegacyUnlocks(api.unlocks(), getLogger()));
        groupCounts = new GroupCounts(this, api.servers(), config.serverListStaleSeconds());
        groupCounts.start(GROUP_COUNTS_INTERVAL_TICKS);
        gadgets = new Gadgets(this, messages, Duration.ofSeconds(config.gadgetCooldownSeconds()));
        friends = new FriendsChannel(this, config.friendsTimeoutTicks());
        previews = new CosmeticPreviews(this, messages, config.cosmeticPreviewTicks());
        var menus = new Menus(this, messages, config, locations, api.unlocks(), api.shop(), api.settings(), api.messages(),
                api.servers(), api.server(), new PremiumRank(this, luckPerms), pets, groupCounts, friends, previews);
        shopNpc = new ShopNpc(this, locations, messages);
        shopNpc.start(config.shopNpcRespawnSeconds());
        var leaderboards = new Leaderboards(this, api.database(), messages, api.messages(), config.leaderboards());
        holograms = new Holograms(this, messages, leaderboards);
        holograms.start(config.leaderboardRefreshSeconds() * 20L);

        var messenger = getServer().getMessenger();
        messenger.registerIncomingPluginChannel(this, WorldDownloaderListener.INIT_CHANNEL, new WorldDownloaderListener(messages));

        hud = new LobbyHud(this, messages);
        hud.start(config.hudRefreshSeconds() * 20L);
        register(previews, hud, new JoinQuitListener(messages, locations, items, trails, pets, hider),
                new CosmeticSettingsListener(messages, trails, pets, gadgets),
                new RoundRecapListener(this, messages, config),
                new LobbyItemListener(messages, menus, hider),
                new ProtectionListener(api.buildMode()),
                new CommandBlockListener(config.allowedCommands()),
                new SecretsListener(this, messages, api.unlocks(), coins, config.secretReward()),
                new ShopNpcListener(menus),
                new PetListener(pets, messages),
                new MoveListener(messages, config, locations, trails),
                new DoubleJumpListener(config),
                new GadgetListener(gadgets),
                new InventoryLockListener(api.buildMode()),
                new DeathListener(locations, items, hider));
        api.presence().maxPlayers(config.maxPlayers());

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = new CommandRegistry(event.registrar(), messages);
            commands.register(SetSpawnCommand.create(locations, messages));
            commands.register(SetHoloCommand.create(messages,
                    Stream.concat(config.holograms().stream(), leaderboards.names().stream()).toList(),
                    holograms, locations, shopNpc));
            commands.register(ClearChatCommand.create(messages));
        });
    }

    @Override
    public void onDisable() {
        if (previews != null) {
            previews.close();
        }
        if (profiles != null) {
            profiles.clearRecentRound();
        }
        if (friends != null) {
            friends.close();
        }
        if (hud != null) {
            hud.stop();
        }
        if (holograms != null) {
            holograms.stop();
        }
        if (shopNpc != null) {
            shopNpc.stop();
        }
        if (groupCounts != null) {
            groupCounts.stop();
        }
        if (gadgets != null) {
            gadgets.restorePaint();
        }
    }

    private void importLegacyUnlocks(LegacyUnlocks legacy) {
        legacy.importYaml(getDataPath().resolve("heads.yml"), key -> Head.byKey(key).map(Head::item));
        legacy.importYaml(getDataPath().resolve("shirts.yml"), key -> Shirt.byKey(key).map(Shirt::item));
        legacy.importYaml(getDataPath().resolve("secrets.yml"), key -> Secret.byKey(key).map(Secret::item));
        legacy.importYaml(getDataPath().resolve("premium-purchases.yml"),
                key -> key.equals("name") ? Optional.of(ShopMenu.PREMIUM_ITEM) : Optional.empty());
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}
