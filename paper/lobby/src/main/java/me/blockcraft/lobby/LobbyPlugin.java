package me.blockcraft.lobby;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.chat.ChatMute;
import me.blockcraft.lobby.command.ClearChatCommand;
import me.blockcraft.lobby.command.GlobalMuteCommand;
import me.blockcraft.lobby.command.SetHoloCommand;
import me.blockcraft.lobby.command.SetSpawnCommand;
import me.blockcraft.lobby.config.LobbyConfig;
import me.blockcraft.lobby.config.LobbyLocation;
import me.blockcraft.lobby.cosmetic.Pets;
import me.blockcraft.lobby.cosmetic.Trails;
import me.blockcraft.lobby.hider.PlayerHider;
import me.blockcraft.lobby.item.LobbyItems;
import me.blockcraft.lobby.listener.ChatMuteListener;
import me.blockcraft.lobby.listener.CommandBlockListener;
import me.blockcraft.lobby.listener.DeathListener;
import me.blockcraft.lobby.listener.DoubleJumpListener;
import me.blockcraft.lobby.listener.InventoryLockListener;
import me.blockcraft.lobby.listener.JoinQuitListener;
import me.blockcraft.lobby.listener.LobbyItemListener;
import me.blockcraft.lobby.listener.MotdListener;
import me.blockcraft.lobby.listener.MoveListener;
import me.blockcraft.lobby.listener.PetListener;
import me.blockcraft.lobby.listener.ProtectionListener;
import me.blockcraft.lobby.listener.SecretsListener;
import me.blockcraft.lobby.listener.ShopNpcListener;
import me.blockcraft.lobby.listener.WorldDownloaderListener;
import me.blockcraft.lobby.menu.Menus;
import me.blockcraft.lobby.npc.ShopNpc;
import me.blockcraft.lobby.rank.PremiumRank;
import me.blockcraft.lobby.rank.TabTeams;
import me.blockcraft.lobby.storage.UnlockStore;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class LobbyPlugin extends JavaPlugin {

    private ShopNpc shopNpc;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        var config = LobbyConfig.fromYaml(YamlFile.loadWithDefaults(getDataPath().resolve("config.yml"), () -> getResource("config.yml")));
        var messages = Messages.load(getDataPath(), () -> getResource("messages.yml"));
        var locations = Locations.load(getDataPath().resolve("locations.yml"), LobbyLocation::key);
        var coins = BlockcraftApi.get().coins();
        var luckPerms = LuckPermsProvider.get();

        var chatMute = new ChatMute();
        var trails = new Trails();
        var pets = new Pets(messages);
        var hider = new PlayerHider(this, config.hiderCooldownMillis());
        var items = new LobbyItems(messages);
        var tabTeams = new TabTeams(luckPerms, messages);
        var secrets = new UnlockStore(getDataPath().resolve("secrets.yml"));
        var menus = new Menus(messages, config, locations, coins,
                new UnlockStore(getDataPath().resolve("heads.yml")),
                new UnlockStore(getDataPath().resolve("shirts.yml")),
                secrets,
                new UnlockStore(getDataPath().resolve("premium-purchases.yml")),
                new PremiumRank(luckPerms), pets, trails);
        shopNpc = new ShopNpc(this, locations, messages);
        shopNpc.start(config.shopNpcRespawnSeconds());

        var messenger = getServer().getMessenger();
        messenger.registerIncomingPluginChannel(this, WorldDownloaderListener.INIT_CHANNEL, new WorldDownloaderListener(messages));

        register(new JoinQuitListener(this, messages, coins, locations, items, tabTeams, trails, pets, hider),
                new LobbyItemListener(messages, menus, items, hider),
                new ProtectionListener(),
                new CommandBlockListener(config.allowedCommands()),
                new ChatMuteListener(chatMute, messages),
                new SecretsListener(messages, secrets, coins, config.secretReward()),
                new ShopNpcListener(menus),
                new PetListener(pets, messages),
                new MoveListener(messages, config, locations, trails),
                new DoubleJumpListener(config),
                new InventoryLockListener(),
                new DeathListener(locations, items),
                new MotdListener(messages, config.maxPlayers()));

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register(SetSpawnCommand.create(locations, messages), "Setzt die Lobby-Teleportpunkte");
            registrar.register(SetHoloCommand.create(messages, locations, shopNpc), "Erstellt Hologramme");
            registrar.register(GlobalMuteCommand.create(chatMute, messages), "Schaltet den Chat stumm");
            registrar.register(ClearChatCommand.create(messages), "Leert den Chat");
        });
    }

    @Override
    public void onDisable() {
        if (shopNpc != null) {
            shopNpc.stop();
        }
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }
}
