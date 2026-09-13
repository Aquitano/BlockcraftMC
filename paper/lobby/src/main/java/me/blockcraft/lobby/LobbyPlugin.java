package me.blockcraft.lobby;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.command.ClearChatCommand;
import me.blockcraft.lobby.command.SetHoloCommand;
import me.blockcraft.lobby.command.SetSpawnCommand;
import me.blockcraft.lobby.config.LobbyConfig;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.Head;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Secret;
import me.blockcraft.lobby.cosmetic.Shirt;
import me.blockcraft.lobby.cosmetic.Trails;
import me.blockcraft.lobby.gadget.Gadgets;
import me.blockcraft.lobby.hider.PlayerHider;
import me.blockcraft.lobby.item.LobbyItems;
import me.blockcraft.lobby.listener.CommandBlockListener;
import me.blockcraft.lobby.listener.DeathListener;
import me.blockcraft.lobby.listener.DoubleJumpListener;
import me.blockcraft.lobby.listener.GadgetListener;
import me.blockcraft.lobby.listener.InventoryLockListener;
import me.blockcraft.lobby.listener.JoinQuitListener;
import me.blockcraft.lobby.listener.LobbyItemListener;
import me.blockcraft.lobby.listener.MoveListener;
import me.blockcraft.lobby.listener.PetListener;
import me.blockcraft.lobby.listener.ProtectionListener;
import me.blockcraft.lobby.listener.SecretsListener;
import me.blockcraft.lobby.listener.ShopNpcListener;
import me.blockcraft.lobby.listener.WorldDownloaderListener;
import me.blockcraft.lobby.menu.Menus;
import me.blockcraft.lobby.menu.ShopMenu;
import me.blockcraft.lobby.network.GroupCounts;
import me.blockcraft.lobby.npc.ShopNpc;
import me.blockcraft.lobby.rank.PremiumRank;
import me.blockcraft.lobby.rank.TabTeams;
import me.blockcraft.lobby.storage.LegacyUnlocks;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.Optional;

public final class LobbyPlugin extends JavaPlugin {

    private static final long GROUP_COUNTS_INTERVAL_TICKS = 100L;

    private ShopNpc shopNpc;
    private GroupCounts groupCounts;
    private Gadgets gadgets;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = LobbyConfig.fromYaml(YamlFile.loadWithDefaults(getDataPath().resolve("config.yml"), () -> getResource("config.yml")));
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"));
        var locations = Locations.load(getDataPath().resolve("locations.yml"), LobbyLocation::key);
        var api = BlockcraftApi.get();
        var coins = api.coins();
        var luckPerms = LuckPermsProvider.get();

        var trails = new Trails();
        var pets = new Pets(messages);
        var hider = new PlayerHider(this, config.hiderCooldownMillis());
        var items = new LobbyItems(messages);
        var tabTeams = new TabTeams(luckPerms, messages);
        importLegacyUnlocks(new LegacyUnlocks(api.unlocks(), getLogger()));
        groupCounts = new GroupCounts(this, api.servers(), config.serverListStaleSeconds());
        groupCounts.start(GROUP_COUNTS_INTERVAL_TICKS);
        gadgets = new Gadgets(this, messages, Duration.ofSeconds(config.gadgetCooldownSeconds()));
        var menus = new Menus(this, messages, config, locations, api.unlocks(), api.shop(), api.settings(), api.messages(),
                api.servers(), api.server(), new PremiumRank(this, luckPerms), pets, trails, gadgets, groupCounts);
        shopNpc = new ShopNpc(this, locations, messages);
        shopNpc.start(config.shopNpcRespawnSeconds());

        var messenger = getServer().getMessenger();
        messenger.registerIncomingPluginChannel(this, WorldDownloaderListener.INIT_CHANNEL, new WorldDownloaderListener(messages));

        register(new JoinQuitListener(this, messages, coins, locations, items, tabTeams, trails, pets, hider),
                new LobbyItemListener(messages, menus, items, hider),
                new ProtectionListener(),
                new CommandBlockListener(config.allowedCommands()),
                new SecretsListener(this, messages, api.unlocks(), coins, config.secretReward()),
                new ShopNpcListener(menus),
                new PetListener(pets, messages),
                new MoveListener(messages, config, locations, trails),
                new DoubleJumpListener(config),
                new GadgetListener(gadgets),
                new InventoryLockListener(),
                new DeathListener(locations, items));
        api.presence().maxPlayers(config.maxPlayers());

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register(SetSpawnCommand.create(locations, messages), "Setzt die Lobby-Teleportpunkte");
            registrar.register(SetHoloCommand.create(messages, locations, shopNpc), "Erstellt Hologramme");
            registrar.register(ClearChatCommand.create(messages), "Leert den Chat");
        });
    }

    @Override
    public void onDisable() {
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
