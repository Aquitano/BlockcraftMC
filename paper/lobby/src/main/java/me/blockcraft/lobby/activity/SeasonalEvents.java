package me.blockcraft.lobby.activity;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
    private enum Trick { PUMPKIN, BATS, GHOST }
    private static final NamespacedKey HAT = new NamespacedKey("lobby", "seasonal_hat");
    private static final int BATS = 6;
    private static final long BAT_TICKS = 60L;
    private static final int GHOST_TICKS = 80;
    static final ResolvableProfile SANTA = ResolvableProfile.resolvableProfile()
            .name("Santa")
            .addProperty(new ProfileProperty("textures",
                    "ewogICJ0aW1lc3RhbXAiIDogMTYxNzg4NTA1NTM5MSwKICAicHJvZmlsZUlkIiA6ICJjN2ZkZGI5YWFkZmQ0YmUzYjMyNmQ3MDBkZjI1MmU4OC"
                            + "IsCiAgInByb2ZpbGVOYW1lIiA6ICJTYW50YSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJT"
                            + "S0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8zZjg2NjgyMWJmYmY1MWMwNWZmNz"
                            + "k4YWYxMDk1NzVhZmZhMThjNzc5MTUxODEwYjUyZTk5YzU0N2MzMzc5ZWI3IgogICAgfQogIH0KfQ==",
                    "CGqEqHjnV96lvUfVkWc90BC0F+pp5a4pT4QUoKQCtSenomZxTZVEWdIqLs/LIUM3OKFDFBfrWpjD8bi3vCsNwYE9OMhcSmDz2QtzJYKncfV7EM"
                            + "iEPP/aS1HF7RtUNrZx/5L9efkeFK1TvmnU45VGOGgfZPH1eerzvcrH+4Pswd3aLNwsivqar9zvOGD1ns/+Wlz5EB7pDiT+RUfSehhTp6xCiXKx"
                            + "ikK7i+ia/G33dK5YTe3928jbdltZ5vZzY2UxudT1fDfvTpoh69XbBcZmCwOwSQlu9iN/IYdInY0nEGRc8Af49mxE8rBrLO0d/G5nmkJ9Vcibh4"
                            + "ieQAqMahN9kb0p50UvMWy6J02n+hYvvnnVpdU5bbbB8lelKBIB4YZhhf6jE+KQ/aRf9roULhAZ/NYYzdKU3NKKq7dOI+y3tDxGrVdhNiepX0H0"
                            + "IjfW1+AMGzLUkirr14ATqTPno36kqFyW3QbkMLFv5H2v10Y6JazzwIDuxPWcZBv+3KVx5MyNMcDgQF5TK/qKZCPo+BH5oCE03mwtS8tD0g4tom"
                            + "kcCuA6+u0c76y3u7jlsqOxBzo4ZO3dk6L/2yuSzSmUSrScaHIUJFV/IdNOEMuA0zh5R/Vs3517iM6Lm8Q+KcQO8S0JBD0RW0U/Lo3QdFNBtG/q"
                            + "VHAysmqHwIumxo+hiMNHJos="))
            .build();

    private final Plugin plugin;
    private final Locations<String> locations;
    private final ActivitiesConfig config;
    private final Messages messages;
    private final NetworkCalendar calendar;
    private final UnlockRewards rewards;
    private final Runnable fireworks;
    private final SeasonalDecor decor;
    private final Map<UUID, Egg> eggs = new HashMap<>();
    private final Set<Chunk> heldChunks = new HashSet<>();
    private final Map<UUID, Hat> hats = new HashMap<>();
    private final Set<UUID> claiming = new HashSet<>();
    private LocalDate day;
    private BossBar countdown;
    private Mannequin santa;

    public SeasonalEvents(Plugin plugin, Locations<String> locations, ActivitiesConfig config, Messages messages,
                          Runnable fireworks, SeasonalDecor decor) {
        this.plugin = plugin;
        this.locations = locations;
        this.config = config;
        this.messages = messages;
        this.calendar = BlockcraftApi.get().calendar();
        this.rewards = new UnlockRewards(BlockcraftApi.get().database());
        this.fireworks = fireworks;
        this.decor = decor;
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
        clear();
        decor.refresh();
        if (calendar.is(CHRISTMAS)) {
            locations.get("santa").ifPresent(this::spawnSanta);
        }
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
            hold(location);
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

    /** Non-persistent entities vanish with their chunk, so a seasonal figure keeps its chunk loaded while it stands. */
    private void hold(Location location) {
        var chunk = location.getChunk();
        chunk.addPluginChunkTicket(plugin);
        heldChunks.add(chunk);
    }

    private void clear() {
        eggs.values().forEach(egg -> { egg.display().remove(); egg.interaction().remove(); });
        eggs.clear();
        if (santa != null) {
            santa.remove();
            santa = null;
        }
        heldChunks.forEach(chunk -> chunk.removePluginChunkTicket(plugin));
        heldChunks.clear();
    }

    private void spawnSanta(Location spot) {
        hold(spot);
        santa = spot.getWorld().spawn(spot, Mannequin.class, figure -> {
            figure.setProfile(SANTA);
            figure.setDescription(null);
            figure.customName(messages.get("seasonal.santa-name"));
            figure.setCustomNameVisible(true);
            figure.setImmovable(true);
            figure.setGravity(false);
            figure.setInvulnerable(true);
            figure.setPersistent(false);
            figure.setRemoveWhenFarAway(false);
        });
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
        if (calendar.is(HALLOWEEN)) {
            player.sendMessage(messages.get("seasonal.halloween-hint", Placeholder.unparsed("phrase", config.halloweenPhrase())));
        }
        calendar.adventDoor(calendar.today()).ifPresent(door ->
                player.sendMessage(messages.get("seasonal.advent-hint", Formatter.number("day", door))));
        if (santa != null) {
            player.sendMessage(messages.get("seasonal.christmas-hint"));
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
                Sounds.TRICK.play(player);
                trick(player);
            }
        }, () -> claiming.remove(player.getUniqueId()));
    }

    private void trick(Player player) {
        var tricks = Trick.values();
        switch (tricks[ThreadLocalRandom.current().nextInt(tricks.length)]) {
            case PUMPKIN -> {
                hat(player, Material.CARVED_PUMPKIN, config.pumpkinDuration().toSeconds());
                player.sendMessage(messages.get("seasonal.trick", Formatter.number("seconds", config.pumpkinDuration().toSeconds())));
            }
            case BATS -> {
                var around = player.getEyeLocation();
                var swarm = new ArrayList<Bat>();
                for (int i = 0; i < BATS; i++) {
                    swarm.add(around.getWorld().spawn(around, Bat.class, bat -> {
                        bat.setPersistent(false);
                        bat.setAwake(true);
                    }));
                }
                Tasks.later(plugin, BAT_TICKS, () -> swarm.forEach(Entity::remove));
                player.sendMessage(messages.get("seasonal.trick-bats"));
            }
            case GHOST -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, GHOST_TICKS, 0, false, false, false));
                Sounds.SCARE.play(player);
                player.sendMessage(messages.get("seasonal.trick-ghost"));
            }
        }
    }

    @EventHandler
    public void onSanta(PlayerInteractEntityEvent event) {
        if (santa == null || !event.getRightClicked().equals(santa)) {
            return;
        }
        event.setCancelled(true);
        var player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND || !calendar.is(CHRISTMAS) || !claiming.add(player.getUniqueId())) {
            return;
        }
        var name = BlockcraftApi.get().realName(player);
        var item = "calendar.christmas." + calendar.today().getYear();
        Tasks.async(plugin, () -> rewards.grant(player.getUniqueId(), name, item, config.christmasCoins()), granted -> {
            claiming.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (granted) {
                jingle(player);
                player.sendMessage(messages.get("seasonal.santa-gift", Formatter.number("coins", config.christmasCoins())));
            } else {
                player.sendMessage(messages.pick("seasonal.santa-talk"));
            }
        }, () -> claiming.remove(player.getUniqueId()));
    }

    @EventHandler
    public void onSantaHit(PrePlayerAttackEntityEvent event) {
        if (santa != null && event.getAttacked().equals(santa)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.pick("seasonal.santa-hurt"));
        }
    }

    private void jingle(Player player) {
        JingleBells.REFRAIN.forEach(note -> Tasks.later(plugin, note.tick(), () -> {
            if (player.isOnline()) {
                Sounds.ADVENT_DOOR.note(player, note.semitone());
            }
        }));
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
        clear();
    }
}
