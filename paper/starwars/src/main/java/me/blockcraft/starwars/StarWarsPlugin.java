package me.blockcraft.starwars;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.command.CommandRegistry;
import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.scenery.FurnitureInteractions;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

public final class StarWarsPlugin extends JavaPlugin implements Listener {
    private Messages messages;
    private StarWarsConfig settings;
    private Map<String, WeaponSpec> weapons;
    private Creatures creatures;
    private Blasters blasters;
    private SceneryActors scenery;
    private NamespacedKey weaponKey;
    private NamespacedKey loanKey;

    @Override public void onEnable() {
        BundledDefaults.apply(this);
        settings = StarWarsConfig.from(getConfig());
        messages = Messages.load(getDataPath(), () -> getResource("messages.yml"), BlockcraftApi.get().tags());
        weapons = WeaponSpec.load(getConfig().getConfigurationSection("weapons"));
        weaponKey = new NamespacedKey(this, "weapon");
        loanKey = new NamespacedKey(this, "loan");
        creatures = new Creatures(this, messages);
        blasters = new Blasters(this, creatures, messages);
        scenery = new SceneryActors(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(creatures, this);
        getServer().getPluginManager().registerEvents(blasters, this);
        var racks = new HashMap<String, Consumer<Player>>();
        weapons.keySet().forEach(id -> racks.put("blockcraft_sw_" + id, player -> give(player, id, true)));
        FurnitureInteractions.register(this, racks);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var root = Commands.literal("arsenal").executes(context -> {
                context.getSource().getSender().sendMessage(messages.get("usage"));
                return 1;
            });
            var test = Commands.literal("test").requires(source -> source.getSender() instanceof Player);
            var give = Commands.literal("give").requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("blockcraft.starwars.admin"));
            weapons.keySet().stream().sorted().forEach(id -> {
                test.then(Commands.literal(id).executes(context -> give(context.getSource().getPlayerOrThrow(), id, true)));
                give.then(Commands.literal(id).executes(context -> give(context.getSource().getPlayerOrThrow(), id, false)));
            });
            root.then(test).then(give);
            var spawn = Commands.literal("spawn").requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("blockcraft.starwars.admin"));
            Creatures.PROFILES.keySet().stream().sorted().forEach(kind -> spawn.then(Commands.literal(kind).executes(context -> {
                var player = context.getSource().getPlayerOrThrow();
                var result = creatures.spawn(kind, player.getLocation(), false);
                player.sendMessage(messages.get(result.isPresent() ? "spawned" : "missing-model",
                        Placeholder.component("creature", messages.get("creatures." + kind))));
                return result.isPresent() ? 1 : 0;
            })));
            root.then(spawn).then(Commands.literal("clear").requires(source -> source.getSender().hasPermission("blockcraft.starwars.admin")).executes(context -> {
                creatures.close();
                context.getSource().getSender().sendMessage(messages.get("cleared"));
                return 1;
            }));
            new CommandRegistry(event.registrar(), messages).register(root.build());
        });
        getServer().getScheduler().runTaskLater(this, () -> {
            exhibits();
            scenery.spawn();
        }, 40);
        getServer().getScheduler().runTaskTimer(this, () -> {
            int now = getServer().getCurrentTick();
            creatures.tick(now);
            blasters.tick(now);
            if (now % 10 == 0) getServer().getOnlinePlayers().stream().filter(player -> !inPractice(player.getLocation())).forEach(this::removeLoans);
        }, 1, 1);
    }

    private void exhibits() {
        if (!settings.practice().enabled()) return;
        var world = getServer().getWorld(settings.practice().world());
        if (world == null) return;
        world.getEntities().stream().filter(entity -> entity.getScoreboardTags().contains("blockcraft.starwars")).forEach(org.bukkit.entity.Entity::remove);
        for (var entry : getConfig().getMapList("practice.exhibits")) {
            if (!(entry.get("kind") instanceof String kind) || !(entry.get("x") instanceof Number x)
                    || !(entry.get("y") instanceof Number y) || !(entry.get("z") instanceof Number z)) continue;
            var at = new Location(world, x.doubleValue(), y.doubleValue(), z.doubleValue(), 180, 0);
            if (!inPractice(at)) continue;
            if (creatures.spawn(kind, at, true).isEmpty()) getLogger().warning("Missing creature model: " + kind);
        }
    }

    boolean inPractice(Location at) {
        var practice = settings.practice();
        return practice.enabled() && at.getWorld().getName().equals(practice.world()) && practice.area().contains(at.toVector());
    }

    StarWarsConfig settings() {
        return settings;
    }

    WeaponSpec weapon(ItemStack item) {
        if (item == null || item.isEmpty()) return null;
        var id = item.getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
        return id == null ? null : weapons.get(id);
    }

    boolean loan(ItemStack item) {
        return item != null && item.getPersistentDataContainer().has(loanKey);
    }

    private int give(Player player, String id, boolean loan) {
        if (loan && !inPractice(player.getLocation())) {
            player.sendMessage(messages.get("practice-only"));
            return 0;
        }
        var current = player.getInventory().getItem(4);
        if (current != null && !current.isEmpty() && !loan(current)) {
            player.sendMessage(messages.get("slot-busy"));
            return 0;
        }
        var item = new ItemStack(Material.PAPER);
        item.editMeta(meta -> {
            meta.itemName(messages.get("weapons." + id));
            meta.setItemModel(NamespacedKey.fromString("blockcraft:props/starwars/" + id));
            meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, id);
            if (loan) meta.getPersistentDataContainer().set(loanKey, PersistentDataType.BYTE, (byte) 1);
        });
        player.getInventory().setItem(4, item);
        player.getInventory().setHeldItemSlot(4);
        if (loan) player.sendMessage(messages.get("loan", Placeholder.component("weapon", messages.get("weapons." + id))));
        return 1;
    }

    private void removeLoans(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) if (loan(inventory.getItem(i))) inventory.setItem(i, null);
        if (loan(player.getItemOnCursor())) player.setItemOnCursor(null);
    }

    @EventHandler public void join(PlayerJoinEvent event) { removeLoans(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { removeLoans(event.getPlayer()); blasters.forget(event.getPlayer()); }
    @EventHandler public void drop(PlayerDropItemEvent event) { if (loan(event.getItemDrop().getItemStack())) event.setCancelled(true); }
    @EventHandler public void swap(PlayerSwapHandItemsEvent event) {
        if (loan(event.getMainHandItem()) || loan(event.getOffHandItem())) event.setCancelled(true);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        var hotbar = event.getHotbarButton() >= 0 ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        if (loan(event.getCurrentItem()) || loan(event.getCursor()) || loan(hotbar)) event.setCancelled(true);
    }
    @EventHandler public void drag(InventoryDragEvent event) { if (loan(event.getOldCursor())) event.setCancelled(true); }
    @EventHandler public void death(PlayerDeathEvent event) { event.getDrops().removeIf(this::loan); }

    @Override public void onDisable() {
        if (scenery != null) scenery.close();
        if (creatures != null) creatures.close();
        if (blasters != null) blasters.close();
        if (loanKey != null) getServer().getOnlinePlayers().forEach(this::removeLoans);
    }
}
