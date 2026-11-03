package me.blockcraft.ttt.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.BlockChanges;
import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.GameSidebar;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.LobbyHotbar;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.RoundStart;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TimerBar;
import me.blockcraft.api.game.LobbyEntrance;
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.arena.MapVote;
import me.blockcraft.ttt.arena.Town;
import me.blockcraft.ttt.config.TttConfig;
import me.blockcraft.ttt.config.TttMode;
import me.blockcraft.ttt.shop.Equipment;
import me.blockcraft.ttt.shop.ShopItem;
import me.blockcraft.ttt.shop.ShopMenu;
import me.blockcraft.ttt.task.Sabotage;
import me.blockcraft.ttt.task.TaskRound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.OptionalInt;

/**
 * One server, one round of TTT, as GommeHD played it. The lobby votes for the map, everyone loots its chests during
 * the preparation, then the roles are drawn: a few traitors who know each other, a detective everyone knows, and
 * innocents. The traitors win when nobody else is left; everyone else wins when the traitors are dead or the time runs
 * out. Nobody hears who killed whom: the dead lie where they fell until someone identifies them, the tester tells a
 * traitor from an innocent, and traitors and detectives spend their kills in a shop. Killing your own side costs
 * karma, and with it the strength of your blows. In TTT 2 the chests stay shut: everyone gets chores with their
 * role, which pay loot and can win the round for the innocents, and the traitors sabotage the town.
 */
public final class TttGame implements ShopMenu.Till, RoundConnections.Seating {

    private enum Stage { PREPARATION, PLAYING }

    private static final long TRAITOR_DOOR_TICKS = 60L;
    private static final Title.Times ROLE_TITLE = Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(750));

    private final Plugin plugin;
    private final TttConfig config;
    private final TttMode mode;
    private final Messages messages;
    private final MapVote vote;
    private final Supplier<Location> lobbySpawn;
    private final KarmaRepository karmaRepository;
    private final RoundSummary summary;
    private final Round round;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final LobbyEntrance entrance;
    private final GameSidebar sidebar;
    private final Corpses corpses;
    private final Equipment equipment;
    private final Channels channels;
    private final CrimeScenes crimeScenes;
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Map<UUID, Integer> karma = new HashMap<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Set<UUID> teamKillers = new HashSet<>();
    private final Set<UUID> karmaLoaded = new HashSet<>();
    private final Set<UUID> ownFault = new HashSet<>();
    /** The name each player had when the roles were drawn, so a traitor who leaves is still named as seen. */
    private final Map<UUID, String> names = new HashMap<>();
    /** When each open traitor door shuts, so a second traitor's click keeps it open the full time. */
    private final Map<Block, Integer> doorsClosing = new HashMap<>();
    private Optional<Town> town = Optional.empty();
    private Optional<Tester> tester = Optional.empty();
    private Optional<TaskRound> tasks = Optional.empty();
    private Stage stage = Stage.PREPARATION;
    private Optional<Faction> winner = Optional.empty();
    private boolean enderChestOpened;
    private int secondsLeft;
    private volatile Set<UUID> traitorChat = Set.of();

    public TttGame(Plugin plugin, GameConfig gameConfig, TttConfig config, TttMode mode, Messages messages, MapVote vote,
                   Supplier<Location> lobbySpawn, StatsRepository stats, StatsMenus statsMenus, KarmaRepository karmaRepository) {
        this.plugin = plugin;
        this.config = config;
        this.mode = mode;
        this.messages = messages;
        this.vote = vote;
        this.lobbySpawn = lobbySpawn;
        this.karmaRepository = karmaRepository;
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(config.rewards().kill(), config.rewards().win()), stats);
        summary.keepKillsSecret();
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.capacity(), gameConfig);
        round.backToLobby(this::backToLobby);
        this.countdown = new LobbyCountdown(plugin, round, gameConfig, config.roundMinimum(), remaining -> { }, this::start);
        this.hotbar = new LobbyHotbar(plugin, List.of(new LobbyHotbar.Choice(MenuIcon.VOTE, messages.get("item.vote"),
                () -> phase() == GamePhase.LOBBY, this::openVote)), statsMenus, countdown);
        round.countWith(this::headcount);
        this.sidebar = new GameSidebar(messages.get(mode == TttMode.TASKS ? "scoreboard.title-tasks" : "scoreboard.title"), round);
        this.corpses = new Corpses(messages);
        this.equipment = new Equipment(plugin, config.shop(), messages, this::roleOf, round::players);
        this.channels = new Channels(plugin, messages, round::players, config.tasks().reach());
        this.crimeScenes = new CrimeScenes(plugin, round::players, config.shop().crimeCameraSeconds());
        this.secondsLeft = config.roundSeconds();
        this.entrance = new LobbyEntrance(round, countdown, hotbar, () -> Optional.of(lobbySpawn.get()),
                () -> Optional.of(phase() == GamePhase.INGAME ? spectatorSpawn() : lobbySpawn.get()),
                OptionalInt.of(config.fullSeconds()), this::loadKarma);
    }

    public GamePhase phase() {
        return round.phase();
    }

    public LobbyCountdown countdown() {
        return countdown;
    }

    public LobbyHotbar hotbar() {
        return hotbar;
    }

    public Equipment equipment() {
        return equipment;
    }

    public Channels channels() {
        return channels;
    }

    public boolean isTesterButton(Block block) {
        return tester.filter(each -> each.isButton(block)).isPresent();
    }

    public boolean isTrapButton(Block block) {
        return tester.filter(each -> each.isTrapButton(block)).isPresent();
    }

    public Corpses corpses() {
        return corpses;
    }

    public boolean isPlaying(Player player) {
        return round.isPlaying(player);
    }

    /** Players fight only once the roles are drawn. */
    public boolean rolesDrawn() {
        return phase() == GamePhase.INGAME && stage == Stage.PLAYING;
    }

    public Optional<Role> roleOf(Player player) {
        return Optional.ofNullable(roles.get(player.getUniqueId()));
    }

    public List<Player> traitors() {
        return round.players().stream().filter(player -> roles.get(player.getUniqueId()) == Role.TRAITOR).toList();
    }

    /** The living traitors, kept as a snapshot because chat reads it off the main thread. */
    public Set<UUID> traitorChat() {
        return traitorChat;
    }

    public double damageFactor(Player attacker) {
        return config.karma().damageFactor(karma(attacker.getUniqueId()));
    }

    @Override
    public void join(Player player) {
        entrance.admit(player);
        tasks.ifPresent(running -> running.show(player));
        refreshAll();
    }

    private Location spectatorSpawn() {
        return town.map(Town::spectatorSpawn).orElseGet(lobbySpawn);
    }

    private void openVote(Player player) {
        if (phase() == GamePhase.LOBBY) {
            vote.open(player);
        }
    }


    @Override
    public void quit(Player player) {
        equipment.forget(player);
        boolean wasPlaying = round.leave(player);
        BlockcraftApi.get().tabTeams().clearColor(player);
        if (phase() == GamePhase.LOBBY) {
            karma.remove(player.getUniqueId());
            vote.withdraw(player);
        }
        if (phase() == GamePhase.INGAME && wasPlaying) {
            updateTraitorChat();
            tasks.ifPresent(TaskRound::update);
            checkWinner();
        }
        refreshAll();
    }

    @Override
    public void vanished(Player player) {
        if (phase() == GamePhase.INGAME && round.isPlaying(player)) {
            quit(player);
            round.watch(player);
        } else if (phase() == GamePhase.LOBBY) {
            vote.withdraw(player);
        }
    }

    private void loadKarma(Player player) {
        var id = player.getUniqueId();
        Tasks.async(plugin, () -> karmaRepository.get(id).orElse(config.karma().start()), value -> {
            karma.putIfAbsent(id, value);
            karmaLoaded.add(id);
            refreshAll();
        });
    }

    private int karma(UUID player) {
        return karma.getOrDefault(player, config.karma().start());
    }

    private void start() {
        var built = vote.decide();
        town = Optional.of(built);
        tester = Optional.of(new Tester(plugin, built.tester(), config.tester(), messages, round::players, equipment::showsAsTraitor));
        tester.get().showLabel();
        tasks = built.tasks().filter(layout -> mode == TttMode.TASKS).map(layout -> new TaskRound(plugin, config.tasks(),
                messages, layout, built.tester().button(), channels, this::roleOf, round::players, this::choresChanged));
        round.enter(GamePhase.INGAME);
        stage = Stage.PREPARATION;
        int index = 0;
        for (var player : round.players()) {
            PlayerReset.reset(player, GameMode.SURVIVAL);
            config.combat().apply(player);
            player.teleport(built.spawn(index++));
        }
        round.spectators().forEach(spectator -> spectator.teleport(built.spectatorSpawn()));
        RoundStart.go(round.players());
        Bukkit.broadcast(messages.get(tasks.isPresent() ? "round.preparation-tasks" : "round.preparation"));
        secondsLeft = config.preparationSeconds();
        round.clock(new TimerBar(messages, "timer.preparation", config.preparationSeconds()), remaining -> {
            secondsLeft = remaining;
            if (round.clockOnSidebar()) {
                refreshAll();
            }
        }, this::drawRoles);
        refreshAll();
    }

    private void drawRoles() {
        var players = new ArrayList<>(round.players());
        if (players.isEmpty()) {
            endRound(Optional.empty());
            return;
        }
        stage = Stage.PLAYING;
        Collections.shuffle(players, ThreadLocalRandom.current());
        roles.putAll(config.roles().assign(players.stream().map(Player::getUniqueId).toList()));
        players.forEach(player -> names.put(player.getUniqueId(), player.getName()));
        var detectives = players.stream().filter(player -> roles.get(player.getUniqueId()) == Role.DETECTIVE).toList();
        for (var player : players) {
            var role = roles.get(player.getUniqueId());
            var name = Placeholder.component("role", role.displayName(messages));
            var goal = messages.get((tasks.isPresent() ? "roles.goal-tasks." : "roles.goal.") + role.key());
            BlockcraftApi.get().notifications().important(player, Title.title(messages.get("roles.title", name), goal, ROLE_TITLE));
            player.sendMessage(messages.get("roles.assigned", name));
            if (role == Role.DETECTIVE) {
                BlockcraftApi.get().tabTeams().color(player, NamedTextColor.BLUE);
            }
            if (role.hasShop()) {
                points.put(player.getUniqueId(), config.shop().startPoints());
                giveShop(player, role);
            }
        }
        var traitors = traitors();
        traitors.forEach(traitor -> {
            var partners = traitors.stream().filter(other -> !other.equals(traitor)).toList();
            traitor.sendMessage(partners.isEmpty() ? messages.get("roles.alone") : messages.get("roles.partners", names(partners)));
        });
        if (!detectives.isEmpty()) {
            Bukkit.broadcast(messages.get("roles.detectives", names(detectives)));
        }
        Sounds.PVP_ENABLED.playToAll();
        updateTraitorChat();
        equipment.start();
        crimeScenes.start();
        tasks.ifPresent(running -> running.assign(Map.copyOf(roles)));
        secondsLeft = config.roundSeconds();
        round.clock(new TimerBar(messages, "timer.round", config.roundSeconds()), remaining -> {
            secondsLeft = remaining;
            round.announceEnd(remaining);
            if (round.clockOnSidebar()) {
                refreshAll();
            }
        }, () -> endRound(Optional.of(Faction.INNOCENTS)));
        refreshAll();
        checkWinner();
    }

    public void death(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        event.deathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (phase() != GamePhase.INGAME || !round.isPlaying(victim)) {
            return;
        }
        var death = summary.resolveDeath(event);
        equipment.forget(victim);
        crimeScenes.died(victim);
        var trapper = tester.flatMap(trap -> trap.trapper(victim.getLocation(), event.getDamageSource().getDamageType().getKey().getKey()))
                .map(Bukkit::getPlayer);
        Optional<Player> killer = ownFault.remove(victim.getUniqueId()) ? Optional.empty()
                : equipment.culprit(victim, event.getDamageSource()).or(death::killer).or(() -> trapper)
                        .filter(other -> !other.equals(victim));
        var victimRole = roleOf(victim);
        killer.ifPresent(other -> victimRole.ifPresent(role -> credit(other, victim, role)));
        summary.death(victim);
        round.eliminate(victim);
        victimRole.ifPresent(role -> corpses.spawn(victim, role, killer));
        round.respawn(victim, 1L);
        updateTraitorChat();
        tasks.ifPresent(TaskRound::update);
        refreshAll();
        checkWinner();
    }

    private void credit(Player killer, Player victim, Role victimRole) {
        var killerRole = roleOf(killer);
        if (killerRole.isEmpty()) {
            return;
        }
        var id = killer.getUniqueId();
        int before = karma(id);
        karma.put(id, config.karma().afterKill(before, killerRole.get(), victimRole));
        if (killerRole.get().sameSide(victimRole)) {
            teamKillers.add(id);
            killer.sendMessage(messages.get("karma.team-kill", Formatter.number("karma", karma(id)),
                    Formatter.number("lost", before - karma(id))));
        } else {
            summary.awardKill(killer, victim, Defeat.OUT);
        }
        int earned = killerRole.get().shopPointsFor(victimRole);
        if (earned > 0 && points.containsKey(id)) {
            points.merge(id, earned, Integer::sum);
            killer.sendMessage(messages.get("shop.earned", Formatter.number("points", points.get(id))));
        }
    }

    public void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        event.setRespawnLocation(phase() == GamePhase.INGAME ? spectatorSpawn() : lobbySpawn.get());
    }

    /** A chest of the map gives one thing and is gone; true if {@code block} was one. */
    public boolean openChest(Player player, Block block) {
        if (block.getType() != Material.CHEST) {
            return false;
        }
        BlockChanges.set(block, Material.AIR);
        ChestLoot.pick(config.chests(), ThreadLocalRandom.current()).map(ChestLoot::item).ifPresent(item -> give(player, item));
        Sounds.MENU_OPEN.play(player);
        return true;
    }

    public void openEnderChest(Player player) {
        if (!rolesDrawn()) {
            player.sendMessage(messages.get("ender-chest.locked"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        if (enderChestOpened) {
            player.sendMessage(messages.get("ender-chest.empty"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        enderChestOpened = true;
        give(player, ItemStack.of(Material.IRON_SWORD));
        player.sendMessage(messages.get("ender-chest.taken"));
        Sounds.PURCHASE.play(player);
    }

    public void pressTester(Player player) {
        if (!rolesDrawn()) {
            player.sendMessage(messages.get("tester.not-ready"));
            return;
        }
        if (tester.isPresent() && equipment.jammed(tester.get().buttonLocation())) {
            player.sendMessage(messages.get("shop.jammer.blocked"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        tester.ifPresent(each -> each.press(player));
    }

    /** The traitor trap opens for living traitors only, once per round; whoever falls is the presser's kill. */
    public void pressTrap(Player player) {
        if (!rolesDrawn() || !round.isPlaying(player) || roleOf(player).filter(role -> role == Role.TRAITOR).isEmpty()) {
            player.sendMessage(messages.get("tester.trap.traitors-only"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        var trap = tester.orElseThrow();
        if (trap.trapUsed()) {
            player.sendMessage(messages.get("tester.trap.used"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        trap.springTrap(player.getUniqueId());
        player.sendMessage(messages.get("tester.trap.sprung"));
    }

    /** The golden sword's wielder struck an innocent and dies of it; nobody earns that kill. */
    public void blameSelf(Player player) {
        ownFault.add(player.getUniqueId());
    }

    /**
     * A click on a corpse. The lighter and the mask work on it without identifying it, since a traitor uses them;
     * anything else identifies it first, and the DNA scanner, the defibrillator and the crime camera then do their part.
     */
    public void inspect(Player finder, Entity corpse, ItemStack hand) {
        var tool = HotbarItem.of(hand, ShopItem.class).filter(ignored -> rolesDrawn());
        if (tool.filter(item -> item == ShopItem.LIGHTER).isPresent()) {
            if (corpses.burn(corpse)) {
                hand.subtract();
                finder.sendMessage(messages.get("shop.lighter.burned"));
                refreshAll();
            }
            return;
        }
        if (tool.filter(item -> item == ShopItem.MASK).isPresent()) {
            corpses.takeFace(corpse).ifPresentOrElse(remains -> {
                hand.subtract();
                equipment.wearMask(finder, remains.name(), remains.profile());
            }, () -> finder.sendMessage(messages.get("shop.mask.no-face")));
            return;
        }
        corpses.inspect(finder, corpse, tool.filter(item -> item == ShopItem.DNA_SCANNER).isPresent())
                .ifPresent(dead -> refreshAll());
        var remains = corpses.remains(corpse);
        if (remains.isEmpty()) {
            return;
        }
        if (tool.filter(item -> item == ShopItem.DEFIBRILLATOR).isPresent()) {
            defibrillate(finder, corpse, remains.get());
        } else if (tool.filter(item -> item == ShopItem.CRIME_CAMERA).isPresent()) {
            if (equipment.jammed(finder.getLocation())) {
                finder.sendMessage(messages.get("shop.jammer.blocked"));
            } else if (!crimeScenes.play(finder, remains.get().player())) {
                finder.sendMessage(messages.get("shop.crime-camera.nothing"));
            }
        }
    }

    /** An innocent who died moments ago lives again after the detective stays at the corpse for a few seconds. */
    private void defibrillate(Player medic, Entity corpse, Corpses.Remains remains) {
        var settings = config.shop().defibrillator();
        if (remains.role() == Role.TRAITOR) {
            medic.sendMessage(messages.get("shop.defibrillator.traitor"));
            return;
        }
        if (Bukkit.getCurrentTick() - remains.diedAt() > settings.windowSeconds() * 20) {
            medic.sendMessage(messages.get("shop.defibrillator.too-late"));
            return;
        }
        if (channels.busy(medic)) {
            return;
        }
        channels.start(medic, remains.location(), settings.holdSeconds(), messages.get("shop.defibrillator.work"), () -> {
            var patient = Bukkit.getPlayer(remains.player());
            var defibrillator = held(medic, ShopItem.DEFIBRILLATOR);
            if (!corpses.isCorpse(corpse) || patient == null || defibrillator.isEmpty() || !round.revive(patient)) {
                medic.sendMessage(messages.get("shop.defibrillator.failed"));
                return;
            }
            defibrillator.get().subtract();
            corpses.remove(corpse);
            PlayerReset.reset(patient, GameMode.SURVIVAL);
            config.combat().apply(patient);
            patient.teleport(remains.location());
            patient.setHealth(settings.health());
            giveShop(patient, remains.role());
            patient.getWorld().playSound(remains.location(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
            Bukkit.broadcast(messages.get("shop.defibrillator.revived", Placeholder.unparsed("player", patient.getName()),
                    Placeholder.unparsed("medic", medic.getName())));
            updateTraitorChat();
            tasks.ifPresent(TaskRound::update);
            refreshAll();
        });
    }

    /** The iron doors to the traitors' passages open for a living traitor, for a moment. */
    public boolean openTraitorDoor(Player player, Block block) {
        var door = town.flatMap(each -> each.traitorDoors().stream()
                .filter(lower -> lower.equals(block) || lower.getRelative(BlockFace.UP).equals(block)).findFirst());
        if (door.isEmpty()) {
            return false;
        }
        if (!rolesDrawn() || roleOf(player).filter(role -> role == Role.TRAITOR).isEmpty()) {
            player.sendMessage(messages.get("traitor-door.locked"));
            Sounds.MENU_DENY.play(player);
            return true;
        }
        var halves = List.of(door.get(), door.get().getRelative(BlockFace.UP));
        setOpen(halves, true);
        player.getWorld().playSound(door.get().getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 1f);
        int closeAt = Bukkit.getCurrentTick() + (int) TRAITOR_DOOR_TICKS;
        doorsClosing.put(door.get(), closeAt);
        Tasks.later(plugin, TRAITOR_DOOR_TICKS, () -> {
            if (doorsClosing.remove(door.get(), closeAt)) {
                setOpen(halves, false);
                door.get().getWorld().playSound(door.get().getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 1f);
            }
        });
        return true;
    }

    /** True if the click on {@code block} was TTT 2's: a chore, the sabotage, a locked door. */
    public boolean clickChore(Player player, Block block) {
        return tasks.filter(running -> running.click(player, block)).isPresent();
    }

    private static void setOpen(List<Block> halves, boolean open) {
        for (var half : halves) {
            if (half.getBlockData() instanceof Openable door) {
                door.setOpen(open);
                BlockChanges.set(half, door);
            }
        }
    }

    private void giveShop(Player player, Role role) {
        if (!role.hasShop()) {
            return;
        }
        var looted = player.getInventory().getItem(RoundItem.SLOT);
        player.getInventory().setItem(RoundItem.SLOT, RoundItem.SHOP.build(
                MenuIcon.TTT_SHOP.item().name(messages.get("item.shop", Placeholder.component("role", role.displayName(messages))))));
        if (looted != null && !looted.isEmpty()) {
            give(player, looted);
        }
    }

    private static Optional<ItemStack> held(Player player, ShopItem good) {
        return Arrays.stream(player.getInventory().getContents())
                .filter(stack -> HotbarItem.of(stack, ShopItem.class).filter(item -> item == good).isPresent())
                .findFirst();
    }

    /** A chore was done: the sidebars show it, and the innocents may have finished. */
    /** Only a chore done can win the round by chores; a death that drops someone's open chores never does. */
    private void choresChanged() {
        refreshAll();
        if (rolesDrawn() && tasks.filter(TaskRound::complete).isPresent()) {
            endRound(Optional.of(Faction.INNOCENTS));
        }
    }

    /** Until their corpse is identified, the dead count as playing, so that the sidebar does not give their death away. */
    private GameSidebar.Headcount headcount() {
        if (phase() != GamePhase.INGAME) {
            return new GameSidebar.Headcount(round.playerCount(), round.spectatorCount());
        }
        var unidentified = corpses.unidentified();
        int hidden = (int) round.spectators().stream().filter(player -> unidentified.contains(player.getUniqueId())).count();
        return new GameSidebar.Headcount(round.playerCount() + hidden, Math.max(0, round.spectatorCount() - hidden));
    }

    public void openShop(Player player) {
        var role = roleOf(player).filter(Role::hasShop);
        if (!rolesDrawn() || !round.isPlaying(player) || role.isEmpty()) {
            return;
        }
        var title = messages.get("shop.title", Placeholder.component("role", role.get().displayName(messages)));
        var sabotage = tasks.filter(running -> role.get() == Role.TRAITOR).map(TaskRound::sabotage);
        new ShopMenu(messages, equipment, this, title, ShopItem.soldTo(role.get()), sabotage, player)
                .open(player, () -> rolesDrawn() && round.isPlaying(player));
    }

    @Override
    public int points(Player player) {
        return points.getOrDefault(player.getUniqueId(), 0);
    }

    @Override
    public int price(ShopItem item) {
        return config.shop().price(item);
    }

    @Override
    public void buy(Player player, ShopItem item) {
        var role = roleOf(player);
        if (!rolesDrawn() || !round.isPlaying(player) || role.isEmpty() || !ShopItem.soldTo(role.get()).contains(item)) {
            return;
        }
        if (equipment.owns(player, item)) {
            player.sendMessage(messages.get("shop.owned"));
            Sounds.MENU_DENY.play(player);
            return;
        }
        int price = price(item);
        int balance = points(player);
        if (balance < price) {
            player.sendMessage(messages.get("shop.too-expensive", Formatter.number("points", price - balance)));
            Sounds.MENU_DENY.play(player);
            return;
        }
        points.put(player.getUniqueId(), balance - price);
        equipment.deliver(player, item);
        player.sendMessage(messages.get("shop.bought", Placeholder.component("item", messages.get("shop.items." + item.key() + ".name"))));
        Sounds.PURCHASE.play(player);
        refreshAll();
    }

    @Override
    public void sabotage(Player player, Sabotage.Kind kind) {
        if (rolesDrawn() && round.isPlaying(player) && roleOf(player).filter(role -> role == Role.TRAITOR).isPresent()) {
            tasks.ifPresent(running -> running.sabotage().trigger(player, kind));
        }
    }

    private void updateTraitorChat() {
        traitorChat = traitors().stream().map(Player::getUniqueId).collect(Collectors.toUnmodifiableSet());
    }

    private void checkWinner() {
        if (!rolesDrawn()) {
            return;
        }
        var alive = round.players().stream().map(player -> roles.get(player.getUniqueId())).filter(Objects::nonNull).toList();
        Faction.winner(alive).ifPresent(faction -> endRound(Optional.of(faction)));
    }

    private void endRound(Optional<Faction> won) {
        if (phase() != GamePhase.INGAME) {
            return;
        }
        winner = won;
        boolean choresDone = tasks.filter(TaskRound::complete).isPresent();
        traitorChat = Set.of();
        equipment.stop();
        channels.clear();
        crimeScenes.stop();
        tester.ifPresent(Tester::stop);
        tasks.ifPresent(TaskRound::stop);
        won.ifPresent(faction -> roles.forEach((id, role) -> {
            var player = Bukkit.getPlayer(id);
            if (role.faction() == faction && player != null) {
                summary.awardWin(player);
            }
        }));
        roles.keySet().forEach(id -> karma.put(id, config.karma().afterRound(karma(id), teamKillers.contains(id))));
        var settled = new HashMap<UUID, Integer>();
        roles.keySet().stream().filter(karmaLoaded::contains).forEach(id -> settled.put(id, karma(id)));
        Tasks.async(plugin, () -> karmaRepository.save(settled));
        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(won.map(faction -> messages.get(choresDone && faction == Faction.INNOCENTS
                        ? "round.winner.chores" : "round.winner." + faction.key()))
                .orElseGet(() -> messages.get("round.draw")));
        var traitorNames = roles.entrySet().stream().filter(entry -> entry.getValue() == Role.TRAITOR)
                .map(entry -> names.get(entry.getKey())).filter(Objects::nonNull).toList();
        if (!traitorNames.isEmpty()) {
            Bukkit.broadcast(messages.get("round.traitors", Placeholder.unparsed("players", String.join(", ", traitorNames))));
        }
        Bukkit.broadcast(Component.empty());
        for (var player : Bukkit.getOnlinePlayers()) {
            BlockcraftApi.get().tabTeams().clearColor(player);
            if (!player.isDead()) {
                backToLobby(player);
            }
        }
        corpses.clear();
        round.end();
        refreshAll();
    }

    /** After the round everyone meets in the lobby again, seen by all, with nothing but the way out. */
    private void backToLobby(Player player) {
        Bukkit.getOnlinePlayers().forEach(other -> other.showPlayer(plugin, player));
        PlayerReset.reset(player, GameMode.ADVENTURE);
        player.teleport(lobbySpawn.get());
        hotbar.giveHub(player);
    }

    @Override
    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    private void show(Player player) {
        var icons = GameSidebar.icons(player);
        var mapName = town.<Component>map(each -> Component.text(each.name())).orElseGet(() -> messages.get("scoreboard.map-vote"));
        var karmaSection = new GameSidebar.Section(messages.get("scoreboard.karma", icons),
                GameSidebar.value(Component.text(karma(player.getUniqueId()))));
        switch (phase()) {
            case LOBBY -> sidebar.lobby(player, config.capacity(), mapName, List.of(karmaSection));
            case INGAME -> {
                var sections = new ArrayList<GameSidebar.Section>();
                var role = roleOf(player);
                var roleName = role.map(each -> each.displayName(messages)).orElseGet(() -> messages.get("scoreboard.no-role"));
                var roleLine = role.filter(Role::hasShop).isPresent() && round.isPlaying(player)
                        ? messages.get("scoreboard.role-points", Placeholder.component("role", roleName),
                                Formatter.number("points", points(player)))
                        : roleName;
                sections.add(new GameSidebar.Section(messages.get("scoreboard.role", icons), GameSidebar.value(roleLine)));
                if (role.filter(each -> each == Role.TRAITOR).isPresent()) {
                    sections.add(new GameSidebar.Section(messages.get("scoreboard.partners", icons), List.of(partners(player))));
                }
                tasks.map(running -> running.duties(player)).filter(duties -> !duties.isEmpty() && round.isPlaying(player))
                        .ifPresent(duties -> sections.add(new GameSidebar.Section(messages.get("scoreboard.chores", icons), duties)));
                sections.add(karmaSection);
                sidebar.round(player, config.capacity(), secondsLeft, mapName, sections);
            }
            case ENDING -> sidebar.end(player, winner.map(faction -> messages.get("scoreboard.winner." + faction.key())).stream().toList());
        }
    }

    /** The other traitors, dead or alive, which only a traitor sees. */
    /** All partners on one line, so that a big round's sidebar still fits the client's fifteen rows. */
    private Component partners(Player viewer) {
        var lines = new ArrayList<Component>();
        roles.forEach((id, role) -> {
            if (role != Role.TRAITOR || id.equals(viewer.getUniqueId())) {
                return;
            }
            var partner = Bukkit.getPlayer(id);
            var name = Placeholder.unparsed("player", names.getOrDefault(id, "?"));
            lines.add(messages.get(partner != null && round.isPlaying(partner) ? "scoreboard.partner-alive" : "scoreboard.partner-dead", name));
        });
        return lines.isEmpty() ? messages.get("scoreboard.partner-none")
                : Component.join(JoinConfiguration.separator(messages.get("scoreboard.partner-separator")), lines);
    }

    private static TagResolver names(List<Player> players) {
        return Placeholder.unparsed("players", String.join(", ", players.stream().map(Player::getName).toList()));
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    public void stop() {
        countdown.cancel();
        equipment.stop();
        channels.clear();
        crimeScenes.stop();
        tasks.ifPresent(TaskRound::stop);
        tester.ifPresent(Tester::stop);
        corpses.clear();
        round.stop();
    }
}
