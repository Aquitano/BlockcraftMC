package me.blockcraft.lobby.activity;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.cosmetic.ChatTag;
import me.blockcraft.api.cosmetic.ChatTagChoice;
import me.blockcraft.api.item.Items;
import me.blockcraft.api.settings.PlayerSettingsLoadedEvent;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.calendar.NetworkCalendar;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.shop.UnlockRewards;
import me.blockcraft.lobby.config.ActivitiesConfig;
import me.blockcraft.lobby.cosmetic.Trail;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static me.blockcraft.common.calendar.NetworkCalendar.Occasion.*;

public final class SeasonalEvents implements Listener, AutoCloseable {

    private record Egg(String key, ItemDisplay display, Interaction interaction) { }
    private record Hat(ItemStack original, ItemStack temporary, long until) { }
    private static final NamespacedKey HAT = new NamespacedKey("lobby", "seasonal_hat");

    private final Plugin plugin;
    private final Locations<String> locations;
    private final ActivitiesConfig config;
    private final Messages messages;
    private final NetworkCalendar calendar;
    private final UnlockRewards rewards;
    private final Runnable fireworks;
    private final Map<UUID, Egg> eggs = new HashMap<>();
    private final Set<Chunk> eggChunks = new HashSet<>();
    private final Map<UUID, Hat> hats = new HashMap<>();
    private final Set<UUID> claiming = new HashSet<>();
    private LocalDate day;
    private BossBar countdown;

    public SeasonalEvents(Plugin plugin, Locations<String> locations, ActivitiesConfig config, Messages messages,
                          Runnable fireworks) {
        this.plugin = plugin;
        this.locations = locations;
        this.config = config;
        this.messages = messages;
        this.calendar = BlockcraftApi.get().calendar();
        this.rewards = new UnlockRewards(BlockcraftApi.get().database());
        this.fireworks = fireworks;
        day = calendar.today();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 20L);
        refresh();
    }

    private void tick() {
        var now = calendar.now();
        var today = now.toLocalDate();
        if (!today.equals(day)) {
            boolean newYear = today.getYear() > day.getYear();
            day = today;
            refresh();
            Bukkit.getOnlinePlayers().forEach(this::arrive);
            if (newYear) {
                fireworks.run();
                Bukkit.broadcast(messages.get("seasonal.new-year", Formatter.number("year", today.getYear())));
            }
        }
        for (var id : Set.copyOf(hats.keySet())) {
            var player = Bukkit.getPlayer(id);
            if (player != null && System.nanoTime() >= hats.get(id).until()) {
                restoreHat(player);
            }
        }
        long left = ChronoUnit.SECONDS.between(now, today.plusDays(1).atStartOfDay(calendar.zone())) + 1;
        if (calendar.is(NEW_YEARS_EVE) && left <= 10) {
            if (countdown == null) {
                countdown = BossBar.bossBar(Component.empty(), 1, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
            }
            countdown.name(messages.get("seasonal.countdown", Formatter.number("seconds", left),
                    Formatter.number("year", today.getYear() + 1))).progress(Math.clamp(left / 10f, 0, 1));
            Bukkit.getOnlinePlayers().forEach(player -> {
                player.showBossBar(countdown);
                Sounds.countdownTick(player, (int) left);
            });
        } else if (countdown != null) {
            Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(countdown));
            countdown = null;
        }
    }

    public void refresh() {
        eggs.values().forEach(egg -> { egg.display().remove(); egg.interaction().remove(); });
        eggs.clear();
        eggChunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        eggChunks.clear();
        if (!calendar.is(EASTER)) {
            return;
        }
        var spots = new ArrayList<String>();
        for (int i = 1; i <= config.easterSpots(); i++) {
            var key = "egg-" + i;
            if (locations.get(key).isPresent()) {
                spots.add(key);
            }
        }
        Collections.shuffle(spots, new Random(day.getYear()));
        for (var key : spots.stream().limit(config.easterEggs()).toList()) {
            var location = locations.get(key).orElseThrow();
            var chunk = location.getChunk();
            chunk.addPluginChunkTicket(plugin);
            eggChunks.add(chunk);
            var display = location.getWorld().spawn(location.clone().add(0, 0.35, 0), ItemDisplay.class, egg -> {
                egg.setItemStack(ItemStack.of(Material.TURTLE_EGG));
                egg.setPersistent(false);
            });
            var interaction = location.getWorld().spawn(location, Interaction.class, target -> {
                target.setInteractionWidth(0.9f);
                target.setInteractionHeight(1);
                target.setResponsive(true);
                target.setPersistent(false);
            });
            eggs.put(interaction.getUniqueId(), new Egg(key, display, interaction));
        }
        Bukkit.getOnlinePlayers().forEach(this::hideFoundEggs);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSettingsLoaded(PlayerSettingsLoadedEvent event) {
        arrive(event.getPlayer());
    }

    private void arrive(Player player) {
        if (calendar.is(BIRTHDAY)) {
            long seconds = ChronoUnit.SECONDS.between(calendar.now(), calendar.today().plusDays(1).atStartOfDay(calendar.zone()));
            hat(player, Material.CAKE, Math.max(1, seconds));
        }
        if (calendar.today().getMonth() == Month.JANUARY) {
            var tag = ChatTagChoice.of(ChatTag.NEW_YEAR, calendar.today().getYear());
            Tasks.async(plugin, () -> BlockcraftApi.get().unlocks().unlock(player.getUniqueId(), tag.item()), granted -> {
                if (granted && player.isOnline()) {
                    player.sendMessage(messages.get("seasonal.tag", Placeholder.component("tag", tag.text(BlockcraftApi.get().messages()))));
                }
            });
        }
        hideFoundEggs(player);
    }

    private String eggPrefix() {
        return "calendar.easter." + day.getYear() + ".";
    }

    private void hideFoundEggs(Player player) {
        if (eggs.isEmpty()) {
            return;
        }
        var prefix = eggPrefix();
        Tasks.async(plugin, () -> BlockcraftApi.get().unlocks().owned(player.getUniqueId(), prefix), owned -> {
            if (player.isOnline()) {
                eggs.values().stream().filter(egg -> owned.contains(prefix + egg.key())).forEach(egg -> hide(player, egg));
            }
        });
    }

    @EventHandler
    public void onEgg(PlayerInteractEntityEvent event) {
        var egg = eggs.get(event.getRightClicked().getUniqueId());
        if (egg == null || event.getHand() != EquipmentSlot.HAND || !calendar.is(EASTER)) {
            return;
        }
        event.setCancelled(true);
        var player = event.getPlayer();
        if (!claiming.add(player.getUniqueId())) {
            return;
        }
        var prefix = eggPrefix();
        var selected = eggs.values().stream().map(Egg::key).toList();
        Tasks.async(plugin, () -> {
            var unlocks = BlockcraftApi.get().unlocks();
            unlocks.unlock(player.getUniqueId(), prefix + egg.key());
            var owned = unlocks.owned(player.getUniqueId(), prefix);
            int found = (int) selected.stream().filter(key -> owned.contains(prefix + key)).count();
            if (found >= config.easterEggs()) {
                unlocks.unlock(player.getUniqueId(), ChatTagChoice.of(ChatTag.EASTER_BUNNY).item());
            }
            return found;
        }, found -> {
            claiming.remove(player.getUniqueId());
            if (player.isOnline()) {
                hide(player, egg);
                Sounds.EASTER_EGG.play(player);
                player.sendMessage(messages.get("seasonal.egg", Formatter.number("found", found), Formatter.number("total", config.easterEggs())));
                if (found >= config.easterEggs()) {
                    player.sendMessage(messages.get("seasonal.tag", Placeholder.component("tag",
                            ChatTagChoice.of(ChatTag.EASTER_BUNNY).text(BlockcraftApi.get().messages()))));
                }
            }
        }, () -> claiming.remove(player.getUniqueId()));
    }

    private void hide(Player player, Egg egg) {
        player.hideEntity(plugin, egg.display());
        player.hideEntity(plugin, egg.interaction());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (calendar.is(HALLOWEEN) && PlainTextComponentSerializer.plainText().serialize(event.message()).strip()
                .equalsIgnoreCase(config.halloweenPhrase())) {
            Tasks.sync(plugin, () -> halloween(event.getPlayer()));
        }
    }

    private void halloween(Player player) {
        if (!calendar.is(HALLOWEEN) || !player.isOnline() || !claiming.add(player.getUniqueId())) {
            return;
        }
        boolean sweet = ThreadLocalRandom.current().nextBoolean();
        var name = BlockcraftApi.get().realName(player);
        var item = "calendar.halloween." + calendar.today();
        Tasks.async(plugin, () -> rewards.grant(player.getUniqueId(), name, item, sweet ? config.halloweenCoins() : 0), granted -> {
            claiming.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (!granted) {
                player.sendMessage(messages.get("seasonal.already"));
            } else if (sweet) {
                Sounds.COINS.play(player);
                player.sendMessage(messages.get("seasonal.sweet", Formatter.number("coins", config.halloweenCoins())));
            } else {
                hat(player, Material.CARVED_PUMPKIN, config.pumpkinDuration().toSeconds());
                Sounds.TRICK.play(player);
                player.sendMessage(messages.get("seasonal.trick", Formatter.number("seconds", config.pumpkinDuration().toSeconds())));
            }
        }, () -> claiming.remove(player.getUniqueId()));
    }

    public void openAdvent(Player player, Consumer<Player> back) {
        var date = calendar.today();
        BlockcraftApi.get().playerTasks().latest(player,
                () -> BlockcraftApi.get().unlocks().owned(player.getUniqueId(), "calendar.advent." + date.getYear() + "."),
                owned -> new AdventMenu(this, messages, date, owned, back).open(player));
    }

    void claimAdvent(Player player, LocalDate date, Consumer<Player> back) {
        if (!calendar.today().equals(date) || calendar.adventDoor(date).isEmpty()) {
            player.sendMessage(messages.get("seasonal.advent-closed"));
            return;
        }
        var name = BlockcraftApi.get().realName(player);
        var currentScreen = BlockcraftApi.get().playerTasks().currentScreen(player);
        BlockcraftApi.get().playerTasks().write(player, () -> {
            boolean granted = rewards.grant(player.getUniqueId(), name, "calendar.advent." + date.getYear() + "." + date.getDayOfMonth(), config.adventCoins());
            if (date.getDayOfMonth() == 24) {
                BlockcraftApi.get().unlocks().unlock(player.getUniqueId(), Trail.SNOW.item());
            }
            return granted;
        }, granted -> {
            player.sendMessage(messages.get(granted ? "seasonal.advent-reward" : "seasonal.already", Formatter.number("coins", config.adventCoins())));
            if (granted) {
                Sounds.ADVENT_DOOR.play(player);
                if (date.getDayOfMonth() == 24) {
                    player.sendMessage(messages.get("seasonal.snow"));
                }
            }
            if (currentScreen.getAsBoolean()) {
                openAdvent(player, back);
            }
        });
    }

    private void hat(Player player, Material material, long seconds) {
        restoreHat(player);
        var original = player.getInventory().getHelmet();
        var temporary = Items.of(material).build();
        temporary.editMeta(meta -> meta.getPersistentDataContainer().set(HAT, PersistentDataType.BYTE, (byte) 1));
        hats.put(player.getUniqueId(), new Hat(original == null ? null : original.clone(), temporary,
                System.nanoTime() + seconds * 1_000_000_000L));
        player.getInventory().setHelmet(temporary);
    }

    private void restoreHat(Player player) {
        var hat = hats.remove(player.getUniqueId());
        if (hat != null && hat.temporary().equals(player.getInventory().getHelmet())) {
            player.getInventory().setHelmet(hat.original());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        restoreHat(event.getPlayer());
        claiming.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            restoreHat(player);
            if (countdown != null) {
                player.hideBossBar(countdown);
            }
        });
        eggs.values().forEach(egg -> { egg.display().remove(); egg.interaction().remove(); });
        eggs.clear();
        eggChunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        eggChunks.clear();
    }
}
