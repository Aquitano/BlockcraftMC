package me.blockcraft.zombies.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.LobbyHotbar;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.game.RoundStart;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.common.message.Messages;
import me.blockcraft.zombies.arena.ArenaMap;
import me.blockcraft.zombies.arena.StationKind;
import me.blockcraft.zombies.config.ZombiesConfig;
import me.blockcraft.zombies.menu.GuideDialog;
import me.blockcraft.zombies.menu.StationMenu;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class ZombiesGame {

    private enum Phase { LOBBY, WAVE, INTERMISSION, ENDING }
    public enum Powerup { MAX_AMMO, DOUBLE_GOLD, INSTANT_KILL }
    private record Drop(Item item, Powerup kind, int expires) { }

    private static final Title.Times TITLE_TIMES = Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(400));
    private final Plugin plugin;
    private final ZombiesConfig config;
    private final Messages messages;
    private final ArenaMap arena;
    private final Round round;
    private final RoundSummary summary;
    private final LobbyCountdown lobbyCountdown;
    private final LobbyHotbar lobbyHotbar;
    private final GameSidebar sidebar;
    private final Weapons weapons;
    private final Enemies enemies;
    private final ArenaEffects effects;
    private final Notes notes;
    private final WaveEvents events;
    private final MysteryBox mysteryBox;
    private final WeaponRacks racks;
    private final Map<UUID, Survivor> survivors = new LinkedHashMap<>();
    private final Map<UUID, Location> downed = new HashMap<>();
    private final Map<UUID, TextDisplay> corpses = new HashMap<>();
    private final Map<UUID, Integer> lastHurt = new HashMap<>();
    private final Map<UUID, Integer> protectedUntil = new HashMap<>();
    private final Map<UUID, Integer> repaired = new HashMap<>();
    private final Map<UUID, Drop> drops = new HashMap<>();
    private final Set<UUID> reviving = new HashSet<>();
    private final BukkitTask task;
    private Phase phase = Phase.LOBBY;
    private Countdown clock;
    private TimerBar timerBar;
    private BossBar waveBar;
    private int tick;
    private int wave;
    private int teamSize;
    private int waveTotal;
    private int doubleGoldUntil;
    private int powerupsThisWave;
    private int instantKillUntil;
    private boolean powered;

    public ZombiesGame(Plugin plugin, GameConfig gameConfig, ZombiesConfig config, Messages messages,
                       ArenaMap arena, StatsRepository stats, StatsMenus statsMenus) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.arena = arena;
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(config.killCoins(), config.winCoins()), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.maxPlayers(), gameConfig);
        this.lobbyCountdown = new LobbyCountdown(plugin, round, gameConfig, 1, remaining -> { }, this::begin);
        this.lobbyHotbar = new LobbyHotbar(plugin,
                List.of(new LobbyHotbar.Choice(MenuIcon.ZOMBIES, messages.get("guide.item"), player -> new GuideDialog(messages).show(player))),
                statsMenus, lobbyCountdown);
        this.sidebar = new GameSidebar(messages.get("scoreboard.title"), round);
        this.weapons = new Weapons(this);
        this.enemies = new Enemies(this);
        this.effects = new ArenaEffects(plugin, arena);
        this.notes = new Notes(messages, arena, config.noteCoins());
        this.events = new WaveEvents(this, effects, config.events());
        this.mysteryBox = new MysteryBox(this);
        this.racks = new WeaponRacks(arena, config.armory());
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        arena.labels(messages);
    }

    public ZombiesConfig config() { return config; }
    public Messages messages() { return messages; }
    public ArenaMap arena() { return arena; }
    public LobbyCountdown lobbyCountdown() { return lobbyCountdown; }
    public LobbyHotbar lobbyHotbar() { return lobbyHotbar; }
    public Weapons weapons() { return weapons; }
    public Enemies enemies() { return enemies; }
    public Notes notes() { return notes; }
    public MysteryBox mysteryBox() { return mysteryBox; }
    public Survivor survivor(Player player) { return survivors.get(player.getUniqueId()); }
    public boolean isLobby() { return phase == Phase.LOBBY; }
    public boolean inWave() { return phase == Phase.WAVE; }
    public boolean running() { return phase == Phase.WAVE || phase == Phase.INTERMISSION; }
    public boolean powerBought() { return powered; }
    public boolean powered() { return powered && !events.powerOut(); }
    public boolean instantKill() { return tick < instantKillUntil; }
    public int teamSize() { return teamSize; }
    public int tickNumber() { return tick; }
    int wave() { return wave; }

    public boolean fighting(Player player) {
        var survivor = survivor(player);
        return running() && survivor != null && survivor.fighting() && round.isPlaying(player) && player.getWorld().equals(arena.world());
    }

    public List<Player> fighters() {
        return round.players().stream().filter(this::fighting).toList();
    }

    public boolean downed(Player player) {
        var survivor = survivor(player);
        return running() && survivor != null && survivor.life() == Survivor.Life.DOWNED;
    }

    public void join(Player player) {
        if (round.join(player)) {
            PlayerReset.reset(player, GameMode.ADVENTURE);
            player.teleport(arena.point("lobby"));
            lobbyHotbar.give(player);
            lobbyCountdown.start();
        } else {
            player.teleport(arena.point("spectator"));
        }
        refreshSidebar();
    }

    public void follow(Player player, UUID friend) {
        round.follow(player, friend);
    }

    public void quit(Player player) {
        boolean participated = survivors.remove(player.getUniqueId()) != null;
        notes.forget(player);
        removeCorpse(player.getUniqueId());
        lastHurt.remove(player.getUniqueId());
        protectedUntil.remove(player.getUniqueId());
        repaired.remove(player.getUniqueId());
        round.leave(player);
        if (running() && participated && fighters().isEmpty()) {
            finish(false);
        }
        refreshSidebar();
    }

    public void vanished(Player player) {
        if (running() && survivor(player) != null) {
            quit(player);
            round.watch(player);
        }
    }

    private void begin() {
        round.enter(GamePhase.INGAME);
        phase = Phase.INTERMISSION;
        teamSize = round.playerCount();
        RoundStart.go(round.players());
        for (var player : round.players()) {
            PlayerReset.reset(player, GameMode.ADVENTURE);
            player.teleport(arena.point("spawn"));
            survivors.put(player.getUniqueId(), new Survivor(config.startingGold()));
            weapons.give(player, Weapon.PISTOL);
            protectedUntil.put(player.getUniqueId(), tick + config.protectionTicks());
        }
        racks.show(tick);
        broadcast("game.started", Formatter.number("players", teamSize), Formatter.number("waves", config.waves().total()));
        intermission();
    }

    private void nextWave() {
        if (!running()) {
            return;
        }
        phase = Phase.WAVE;
        wave++;
        repaired.clear();
        teamSize = round.playerCount();
        var plan = WavePlan.forSquad(wave, teamSize, config.waves());
        waveTotal = plan.enemies();
        powerupsThisWave = 0;
        enemies.begin(plan);
        events.begin(plan);
        if (!events.bloodMoon()) {
            title(plan.boss() ? "wave.boss-title" : plan.hounds() ? "wave.hound-title" : "wave.title", "wave.subtitle",
                    Formatter.number("wave", wave), Formatter.number("enemies", plan.enemies()));
            effects.horn(plan.boss());
        }
        Sounds.COUNTDOWN_GO.playToAll();
        waveBar = BossBar.bossBar(Component.empty(), 1, plan.boss() || events.bloodMoon() ? BossBar.Color.RED : BossBar.Color.GREEN,
                BossBar.Overlay.NOTCHED_10);
        timerBar = new TimerBar(messages, "timer.wave", config.waveSeconds());
        clock = round.clock(timerBar, remaining -> refreshSidebar(), () -> finish(false));
    }

    private void clearWave() {
        events.end();
        clock.cancel();
        timerBar.hide();
        hideWaveBar();
        phase = Phase.INTERMISSION;
        for (var player : round.players()) {
            addGold(player, config.clearGold());
            summary.awardCoins(player, config.waveCoins());
            if (!survivor(player).fighting()) {
                recover(player, true);
            }
        }
        broadcast("wave.cleared", Formatter.number("wave", wave), Formatter.number("gold", config.clearGold()));
        if (wave >= config.waves().total()) {
            finish(true);
        } else {
            title("wave.cleared-title", "wave.cleared-subtitle", Formatter.number("wave", wave),
                    Formatter.number("seconds", config.intermissionSeconds()));
            Sounds.LEVEL_UP.playToAll();
            intermission();
        }
    }

    private void intermission() {
        timerBar = new TimerBar(messages, "timer.intermission", config.intermissionSeconds());
        clock = round.clock(timerBar, remaining -> {
            if (remaining <= 3 && remaining > 0) {
                Sounds.countdownTick(remaining);
            }
            refreshSidebar();
        }, this::nextWave);
    }

    private void tick() {
        tick++;
        if (phase != Phase.ENDING) {
            effects.tick(tick, !inWave());
        }
        if (!running()) {
            return;
        }
        round.players().forEach(player -> weapons.tick(player, tick));
        weapons.burn(tick);
        mysteryBox.tick(tick);
        racks.tick(tick);
        if (inWave()) {
            enemies.tick(tick);
            events.tick(tick);
            if (inWave() && enemies.remaining() == 0) {
                clearWave();
            }
        }
        if (!running()) {
            return;
        }
        if (tick % 5 == 0) {
            revives();
            drops.values().removeIf(drop -> {
                if (tick >= drop.expires() || !drop.item().isValid()) {
                    drop.item().remove();
                    return true;
                }
                return false;
            });
            hud();
        }
        if (tick % config.repairTicks() == 0) {
            repair();
        }
        if (tick % 20 == 0) {
            for (var player : fighters()) {
                if (tick - lastHurt.getOrDefault(player.getUniqueId(), 0) >= config.healthRegenTicks()) {
                    player.setHealth(Math.min(maxHealth(player), player.getHealth() + 1));
                }
            }
        }
    }

    public void hurt(Player player, double damage) {
        if (!fighting(player) || tick < protectedUntil.getOrDefault(player.getUniqueId(), 0) || damage <= 0
                || tick - lastHurt.getOrDefault(player.getUniqueId(), -config.damageImmunityTicks()) < config.damageImmunityTicks()) {
            return;
        }
        lastHurt.put(player.getUniqueId(), tick);
        player.playHurtAnimation(0);
        if (player.getHealth() > damage) {
            player.setHealth(player.getHealth() - damage);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 1);
            return;
        }
        var survivor = survivor(player);
        if (round.playerCount() == 1 && survivor.perks().remove(Perk.QUICK_REVIVE)) {
            player.setHealth(maxHealth(player) / 2);
            protectedUntil.put(player.getUniqueId(), tick + config.protectionTicks());
            player.sendMessage(messages.get("revive.solo"));
            Sounds.HEAL.play(player);
            return;
        }
        survivor.down(tick, config.bleedTicks());
        Menu.close(player);
        player.closeDialog();
        player.setHealth(1);
        player.setCollidable(false);
        var location = player.getLocation();
        downed.put(player.getUniqueId(), location);
        corpses.put(player.getUniqueId(), arena.world().spawn(location.clone().add(0, 2, 0), TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false);
            display.setSeeThrough(true);
        }));
        broadcast("revive.downed", Placeholder.unparsed("player", player.getName()), Formatter.number("seconds", config.bleedTicks() / 20));
        Sounds.NOTIFY.playToAll();
        if (fighters().isEmpty()) {
            finish(false);
        }
    }

    private void revives() {
        reviving.clear();
        for (var player : round.players()) {
            var survivor = survivor(player);
            if (survivor.life() != Survivor.Life.DOWNED) {
                continue;
            }
            if (survivor.bleed(tick)) {
                summary.death(player);
                removeCorpse(player.getUniqueId());
                player.setGameMode(GameMode.SPECTATOR);
                broadcast("revive.out", Placeholder.unparsed("player", player.getName()));
                continue;
            }
            var location = downed.get(player.getUniqueId());
            var helper = fighters().stream().filter(other -> other.isSneaking()
                    && other.getLocation().distanceSquared(location) <= config.reviveRadius() * config.reviveRadius()
                    && other.hasLineOfSight(player)).findFirst();
            helper.ifPresent(other -> reviving.add(other.getUniqueId()));
            int speed = helper.map(other -> survivor(other).perks().contains(Perk.QUICK_REVIVE) ? 10 : 5).orElse(0);
            if (survivor.revive(speed, config.reviveTicks())) {
                recover(player, false);
                helper.ifPresent(other -> {
                    survivor(other).helped();
                    Sounds.HEAL.play(other);
                });
                broadcast("revive.saved", Placeholder.unparsed("player", player.getName()));
                continue;
            }
            var resolvers = TagResolver.resolver(Placeholder.unparsed("player", player.getName()),
                    Formatter.number("seconds", Math.max(0, survivor.bleedAt() - tick) / 20),
                    Formatter.number("progress", (int) (100.0 * survivor.reviveProgress() / config.reviveTicks())));
            var corpse = corpses.get(player.getUniqueId());
            if (corpse != null) {
                corpse.text(messages.get("revive.marker", resolvers));
            }
            helper.ifPresent(other -> BlockcraftApi.get().notifications().status(other, messages.get("revive.helping", resolvers)));
        }
    }

    private void recover(Player player, boolean respawn) {
        var location = downed.getOrDefault(player.getUniqueId(), arena.point("spawn"));
        survivor(player).recover();
        removeCorpse(player.getUniqueId());
        player.setGameMode(GameMode.ADVENTURE);
        player.setCollidable(true);
        player.teleport(respawn ? arena.point("spawn") : location);
        applyHealth(player);
        player.setHealth(respawn ? maxHealth(player) : maxHealth(player) / 2);
        protectedUntil.put(player.getUniqueId(), tick + config.protectionTicks());
        weapons.render(player);
        Sounds.HEAL.play(player);
    }

    private void removeCorpse(UUID id) {
        downed.remove(id);
        var corpse = corpses.remove(id);
        if (corpse != null) {
            corpse.remove();
        }
    }

    private void repair() {
        for (var player : fighters()) {
            if (!player.isSneaking() || player.getNearbyEntities(3, 3, 3).stream().anyMatch(enemies::contains)) {
                continue;
            }
            for (var window : arena.activeWindows()) {
                if (player.getLocation().distanceSquared(window.entry()) > 9) {
                    continue;
                }
                var missing = arena.repair(window);
                if (missing.isPresent()) {
                    var block = missing.get();
                    int earned = Math.min(config.repairGold(), Math.max(0, config.repairLimit() - repaired.getOrDefault(player.getUniqueId(), 0)));
                    repaired.merge(player.getUniqueId(), earned, Integer::sum);
                    addGold(player, earned);
                    BlockcraftApi.get().notifications().feedback(player, messages.get("window.repaired", Formatter.number("gold", earned)));
                    arena.world().playSound(block.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.5f, 1.2f);
                }
                break;
            }
        }
    }

    public void addGold(Player player, int gold) {
        var survivor = survivor(player);
        if (survivor != null) {
            survivor.addGold(gold * (tick < doubleGoldUntil ? 2 : 1));
        }
    }

    public void killed(Player player, Mob mob, Location location) {
        if (!running()) {
            return;
        }
        events.killed(mob, player);
        if (player == null || survivor(player) == null) {
            return;
        }
        summary.awardMobKill(player);
        addGold(player, events.killGold(config.killGold()));
        Sounds.KILL.play(player);
        if (powerupsThisWave < config.powerupsPerWave() && ThreadLocalRandom.current().nextDouble() < config.dropChance()) {
            powerupsThisWave++;
            var kind = Powerup.values()[ThreadLocalRandom.current().nextInt(Powerup.values().length)];
            var item = arena.world().dropItem(location, ItemStack.of(switch (kind) {
                case MAX_AMMO -> Material.FIREWORK_STAR;
                case DOUBLE_GOLD -> Material.GOLD_INGOT;
                case INSTANT_KILL -> Material.SKELETON_SKULL;
            }));
            item.customName(messages.get("powerup." + kind.name().toLowerCase(java.util.Locale.ROOT)));
            item.setCustomNameVisible(true);
            item.setGlowing(true);
            item.setPersistent(false);
            item.setVelocity(new org.bukkit.util.Vector());
            drops.put(item.getUniqueId(), new Drop(item, kind, tick + config.powerupTicks()));
        }
    }

    public boolean collect(Player player, Item item) {
        var drop = drops.get(item.getUniqueId());
        if (drop == null) {
            return false;
        }
        if (fighting(player)) {
            drops.remove(item.getUniqueId());
            item.remove();
            switch (drop.kind()) {
                case MAX_AMMO -> round.players().forEach(other -> {
                    survivor(other).weapons().values().forEach(Ammo::refill);
                    weapons.render(other);
                });
                case DOUBLE_GOLD -> doubleGoldUntil = tick + config.powerupTicks();
                case INSTANT_KILL -> instantKillUntil = tick + config.powerupTicks();
            }
            broadcast("powerup.collected", Placeholder.component("powerup", messages.get("powerup." + drop.kind().name().toLowerCase(java.util.Locale.ROOT))));
            Sounds.PURCHASE.playToAll();
        }
        return true;
    }

    public void openStation(Player player, ArenaMap.Station station) {
        if (canUse(player, station) && !mysteryBox.click(player, station)) {
            new StationMenu(this, player, station).open(player, () -> canUse(player, station));
        }
    }

    public boolean canUse(Player player, ArenaMap.Station station) {
        return fighting(player) && arena.unlocked(station.room()) && player.getLocation().distanceSquared(station.location()) < 25;
    }

    public boolean purchase(Player player, ArenaMap.Station station, int price, boolean available, Runnable grant) {
        if (!canUse(player, station)) {
            return false;
        }
        if (!available) {
            feedback(player, "shop.unavailable");
            return false;
        }
        if (!survivor(player).spend(price)) {
            feedback(player, "shop.not-enough");
            return false;
        }
        grant.run();
        weapons.render(player);
        Sounds.PURCHASE.play(player);
        BlockcraftApi.get().notifications().feedback(player, messages.get("shop.bought", Formatter.number("gold", survivor(player).gold())));
        refreshSidebar();
        return true;
    }

    public void buyDoor(Player player, ArenaMap.Room room) {
        if (!fighting(player) || arena.unlocked(room.id())) {
            return;
        }
        if (!survivor(player).spend(room.price())) {
            feedback(player, "shop.not-enough");
            return;
        }
        effects.openDoor(room);
        broadcast("room.opened", Placeholder.unparsed("player", player.getName()), Placeholder.component("room", messages.get("room." + room.id())));
        Sounds.PURCHASE.playToAll();
    }

    public void powerOn(ArenaMap.Station generator) {
        powered = true;
        broadcast("game.powered");
        effects.powerOn(generator.location());
    }

    public void teamRevive() {
        round.players().stream().filter(player -> !survivor(player).fighting()).forEach(player -> recover(player, true));
    }

    public boolean needsRevive() {
        return round.players().stream().anyMatch(player -> !survivor(player).fighting());
    }

    public void armor(Player player) {
        var survivor = survivor(player);
        survivor.armor(survivor.armor() + 1);
        var inventory = player.getInventory();
        boolean iron = survivor.armor() == 1;
        inventory.setBoots(unbreakable(iron ? Material.IRON_BOOTS : Material.DIAMOND_BOOTS));
        inventory.setLeggings(unbreakable(iron ? Material.IRON_LEGGINGS : Material.DIAMOND_LEGGINGS));
        inventory.setChestplate(unbreakable(iron ? Material.IRON_CHESTPLATE : Material.DIAMOND_CHESTPLATE));
        inventory.setHelmet(unbreakable(iron ? Material.IRON_HELMET : Material.DIAMOND_HELMET));
    }

    private static ItemStack unbreakable(Material material) {
        var item = ItemStack.of(material);
        item.editMeta(meta -> meta.setUnbreakable(true));
        return item;
    }

    public void applyHealth(Player player) {
        Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(
                20 + (survivor(player).perks().contains(Perk.EXTRA_HEALTH) ? config.extraHealth() : 0));
        player.setHealth(Math.min(player.getHealth(), maxHealth(player)));
    }

    public void grantPerk(Player player, Perk perk) {
        double previousMax = maxHealth(player);
        survivor(player).perks().add(perk);
        applyHealth(player);
        player.setHealth(Math.min(maxHealth(player), player.getHealth() + maxHealth(player) - previousMax));
    }

    private static double maxHealth(Player player) {
        return Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
    }

    public void unexpectedDeath(Player player) {
        if (running() && survivor(player) != null) {
            summary.death(player);
            survivor(player).die();
            removeCorpse(player.getUniqueId());
            if (fighters().isEmpty()) {
                finish(false);
            }
        }
    }

    private void hud() {
        for (var player : fighters()) {
            if (reviving.contains(player.getUniqueId()) || events.holding(player)) {
                continue;
            }
            var held = weapons.held(player);
            if (held.isEmpty()) {
                continue;
            }
            var ammo = survivor(player).weapons().get(held.get());
            BlockcraftApi.get().notifications().status(player, messages.get(ammo.reloading() ? "hud.reload" : "hud.weapon",
                    Placeholder.component("weapon", messages.get("weapon." + held.get().key())),
                    Formatter.number("clip", ammo.clip()), Formatter.number("reserve", ammo.reserve()),
                    Formatter.number("gold", survivor(player).gold()), Formatter.number("seconds", Math.max(0, ammo.reloadAt() - tick) / 20.0)));
        }
        for (var player : round.players()) {
            if (downed(player)) {
                BlockcraftApi.get().notifications().status(player, messages.get("hud.downed",
                        Formatter.number("seconds", Math.max(0, survivor(player).bleedAt() - tick) / 20)));
            }
        }
        if (waveBar != null && inWave()) {
            waveBar.name(messages.get("wave.bar", Formatter.number("wave", wave), Formatter.number("remaining", enemies.remaining()),
                            Formatter.number("total", config.waves().total())))
                    .progress(Math.clamp(enemies.remaining() / (float) waveTotal, 0, 1));
            Bukkit.getServer().showBossBar(waveBar);
        }
    }

    public void refreshSidebar() {
        for (var player : Bukkit.getOnlinePlayers()) {
            if (isLobby()) {
                sidebar.lobby(player, config.maxPlayers(), Component.text(arena.name()), List.of());
            } else if (phase == Phase.ENDING) {
                sidebar.end(player, summary.winnerNames().stream().<Component>map(Component::text).toList());
            } else {
                var values = new ArrayList<Component>();
                values.add(messages.get("scoreboard.wave", Formatter.number("wave", wave), Formatter.number("total", config.waves().total())));
                values.add(messages.get("scoreboard.enemies", Formatter.number("enemies", enemies.remaining())));
                var survivor = survivor(player);
                if (survivor != null) {
                    values.add(messages.get("scoreboard.gold", Formatter.number("gold", survivor.gold())));
                    values.add(messages.get("scoreboard.state." + survivor.life().name().toLowerCase(java.util.Locale.ROOT)));
                }
                var sections = new ArrayList<GameSidebar.Section>();
                sections.add(new GameSidebar.Section(messages.get("scoreboard.squad", GameSidebar.icons(player)), values));
                events.section(player).ifPresent(sections::add);
                sidebar.round(player, config.maxPlayers(), clock == null ? 0 : clock.remaining(), Component.text(arena.name()), sections);
            }
        }
    }

    public void feedback(Player player, String key) {
        BlockcraftApi.get().notifications().feedback(player, messages.get(key));
        Sounds.MENU_DENY.play(player);
    }

    private void finish(boolean won) {
        if (!running()) {
            return;
        }
        phase = Phase.ENDING;
        events.end();
        if (!won) {
            summary.defeatedBy(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(messages.get("game.enemy-name")));
        }
        enemies.clear();
        mysteryBox.clear();
        racks.clear();
        hideWaveBar();
        drops.values().forEach(drop -> drop.item().remove());
        drops.clear();
        for (var player : round.players()) {
            if (!won && survivor(player).life() == Survivor.Life.DOWNED) {
                summary.death(player);
            }
            summary.note(player, messages.get("summary.wave", Formatter.number("wave", wave), Formatter.number("total", config.waves().total())));
            summary.note(player, messages.get("summary.revives", Formatter.number("revives", survivor(player).revives())));
            if (won) {
                summary.awardWin(player);
            }
            removeCorpse(player.getUniqueId());
            PlayerReset.reset(player, GameMode.ADVENTURE);
            player.teleport(arena.point("lobby"));
            lobbyHotbar.giveHub(player);
        }
        broadcast(won ? "game.won" : "game.lost", Formatter.number("wave", wave));
        round.end();
        refreshSidebar();
    }

    private void hideWaveBar() {
        if (waveBar != null) {
            Bukkit.getServer().hideBossBar(waveBar);
            waveBar = null;
        }
    }

    public void stop() {
        task.cancel();
        phase = Phase.ENDING;
        lobbyCountdown.cancel();
        round.stop();
        events.end();
        enemies.clear();
        mysteryBox.clear();
        racks.clear();
        hideWaveBar();
        drops.values().forEach(drop -> drop.item().remove());
        List.copyOf(corpses.keySet()).forEach(this::removeCorpse);
        arena.restore();
    }

    void broadcast(String key, TagResolver... tags) {
        Bukkit.broadcast(messages.get(key, tags));
    }

    /** Shows {@code key.title} and {@code key.subtitle} with {@code sound} to everyone and writes {@code key.announce} to chat. */
    void announce(String key, Sounds sound, TagResolver... tags) {
        Bukkit.getOnlinePlayers().forEach(player -> BlockcraftApi.get().notifications().important(player,
                Title.title(messages.get(key + ".title", tags), messages.get(key + ".subtitle", tags), TITLE_TIMES), sound));
        broadcast(key + ".announce", tags);
    }

    private void title(String title, String subtitle, TagResolver... tags) {
        Bukkit.getOnlinePlayers().forEach(player -> BlockcraftApi.get().notifications().important(player,
                Title.title(messages.get(title, tags), messages.get(subtitle, tags), TITLE_TIMES)));
    }
}
