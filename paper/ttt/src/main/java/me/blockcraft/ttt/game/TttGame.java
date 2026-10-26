package me.blockcraft.ttt.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.Defeat;
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
import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.arena.Town;
import me.blockcraft.ttt.config.TttConfig;
import me.blockcraft.ttt.shop.Equipment;
import me.blockcraft.ttt.shop.ShopItem;
import me.blockcraft.ttt.shop.ShopMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
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
import java.util.stream.Collectors;

/**
 * One server, one round of TTT, as GommeHD played it. Everyone loots the town's chests during the preparation, then
 * the roles are drawn: a few traitors who know each other, a detective everyone knows, and innocents. The traitors
 * win when nobody else is left; everyone else wins when the traitors are dead or the time runs out. Nobody hears
 * who killed whom: the dead lie where they fell until someone identifies them, the tester tells a traitor from an
 * innocent, and traitors and detectives spend their kills in a shop. Killing your own side costs karma, and with
 * it the strength of your blows.
 */
public final class TttGame implements ShopMenu.Till {

    private enum Stage { PREPARATION, PLAYING }

    private static final Title.Times ROLE_TITLE = Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(750));

    private final Plugin plugin;
    private final TttConfig config;
    private final Messages messages;
    private final Town town;
    private final KarmaRepository karmaRepository;
    private final RoundSummary summary;
    private final Round round;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final GameSidebar sidebar;
    private final Corpses corpses;
    private final Equipment equipment;
    private final Tester tester;
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Map<UUID, Integer> karma = new HashMap<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Set<UUID> teamKillers = new HashSet<>();
    private final Set<UUID> karmaLoaded = new HashSet<>();
    private final Set<UUID> ownFault = new HashSet<>();
    /** The name each player had when the roles were drawn, so a traitor who leaves is still named as seen. */
    private final Map<UUID, String> names = new HashMap<>();
    private Stage stage = Stage.PREPARATION;
    private Optional<Faction> winner = Optional.empty();
    private boolean enderChestOpened;
    private int secondsLeft;
    private volatile Set<UUID> traitorChat = Set.of();

    public TttGame(Plugin plugin, GameConfig gameConfig, TttConfig config, Messages messages, Town town,
                   StatsRepository stats, StatsMenus statsMenus, KarmaRepository karmaRepository) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.town = town;
        this.karmaRepository = karmaRepository;
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(config.rewards().kill(), config.rewards().win()), stats);
        summary.keepKillsSecret();
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.capacity(), gameConfig);
        this.countdown = new LobbyCountdown(plugin, round, gameConfig, config.roundMinimum(), remaining -> { }, this::start);
        this.hotbar = new LobbyHotbar(plugin, List.of(), statsMenus, countdown);
        this.sidebar = new GameSidebar(messages.get("scoreboard.title"), round);
        this.corpses = new Corpses(messages);
        this.equipment = new Equipment(plugin, config.shop(), messages, this::roleOf, round::players);
        this.tester = new Tester(plugin, town.tester(), config.tester(), messages, round::players, equipment::showsAsTraitor);
        this.secondsLeft = config.roundSeconds();
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

    public Town town() {
        return town;
    }

    public Equipment equipment() {
        return equipment;
    }

    public Tester tester() {
        return tester;
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

    public void join(Player player) {
        if (round.join(player)) {
            PlayerReset.reset(player, GameMode.ADVENTURE);
            player.teleport(town.lobbySpawn());
            hotbar.give(player);
            loadKarma(player);
            countdown.start();
            if (round.playerCount() >= config.capacity()) {
                countdown.shorten(config.fullSeconds());
            }
        } else {
            player.teleport(phase() == GamePhase.INGAME ? town.spectatorSpawn() : town.lobbySpawn());
        }
        refreshAll();
    }

    public void follow(Player spectator, UUID friend) {
        round.follow(spectator, friend);
    }

    public void quit(Player player) {
        boolean wasPlaying = round.leave(player);
        BlockcraftApi.get().tabTeams().clearColor(player);
        if (phase() == GamePhase.LOBBY) {
            karma.remove(player.getUniqueId());
        }
        if (phase() == GamePhase.INGAME && wasPlaying) {
            updateTraitorChat();
            checkWinner();
        }
        refreshAll();
    }

    public void vanished(Player player) {
        if (phase() == GamePhase.INGAME && round.isPlaying(player)) {
            quit(player);
            round.watch(player);
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
        round.enter(GamePhase.INGAME);
        stage = Stage.PREPARATION;
        int index = 0;
        for (var player : round.players()) {
            PlayerReset.reset(player, GameMode.SURVIVAL);
            config.combat().apply(player);
            player.teleport(town.spawn(index++));
        }
        round.spectators().forEach(spectator -> spectator.teleport(town.spectatorSpawn()));
        RoundStart.go(round.players());
        Bukkit.broadcast(messages.get("round.preparation", Formatter.number("seconds", config.preparationSeconds())));
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
            BlockcraftApi.get().notifications().important(player,
                    Title.title(messages.get("roles.title", name), messages.get("roles.goal." + role.key()), ROLE_TITLE));
            player.sendMessage(messages.get("roles.assigned", name));
            player.sendMessage(messages.get("roles.goal." + role.key()));
            if (role == Role.DETECTIVE) {
                BlockcraftApi.get().tabTeams().color(player, NamedTextColor.BLUE);
            }
            if (role.hasShop()) {
                points.put(player.getUniqueId(), config.shop().startPoints());
                var looted = player.getInventory().getItem(RoundItem.SLOT);
                player.getInventory().setItem(RoundItem.SLOT, RoundItem.SHOP.build(player,
                        MenuIcon.TTT_SHOP.item().name(messages.get("item.shop", name))));
                if (looted != null && !looted.isEmpty()) {
                    give(player, looted);
                }
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
        Bukkit.broadcast(messages.get("roles.traitor-count", Formatter.number("traitors", traitors.size())));
        Sounds.PVP_ENABLED.playToAll();
        updateTraitorChat();
        equipment.start();
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
        Optional<Player> killer = ownFault.remove(victim.getUniqueId()) ? Optional.empty()
                : equipment.ownerOf(event.getDamageSource().getDirectEntity()).or(death::killer)
                        .filter(other -> !other.equals(victim));
        var victimRole = roleOf(victim);
        killer.ifPresent(other -> victimRole.ifPresent(role -> credit(other, victim, role)));
        summary.death(victim);
        round.eliminate(victim);
        victimRole.ifPresent(role -> corpses.spawn(victim, role, killer));
        round.respawn(victim, 1L);
        updateTraitorChat();
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
        event.setRespawnLocation(phase() == GamePhase.INGAME ? town.spectatorSpawn() : town.lobbySpawn());
        if (round.isSpectator(player)) {
            Tasks.sync(plugin, () -> round.watch(player));
        }
    }

    /** A town chest gives one thing and is gone; true if {@code block} was one. */
    public boolean openChest(Player player, Block block) {
        if (!town.chests().contains(block) || block.getType() != Material.CHEST) {
            return false;
        }
        block.setType(Material.AIR, false);
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
        tester.press(player);
    }

    /** The golden sword's wielder struck an innocent and dies of it; nobody earns that kill. */
    public void blameSelf(Player player) {
        ownFault.add(player.getUniqueId());
    }

    public void inspect(Player finder, Entity corpse, ItemStack hand) {
        corpses.inspect(finder, corpse, HotbarItem.of(hand, ShopItem.class).filter(item -> item == ShopItem.DNA_SCANNER).isPresent());
    }

    public void openShop(Player player) {
        var role = roleOf(player).filter(Role::hasShop);
        if (!rolesDrawn() || !round.isPlaying(player) || role.isEmpty()) {
            return;
        }
        var title = messages.get("shop.title", Placeholder.component("role", role.get().displayName(messages)));
        new ShopMenu(messages, equipment, this, title, ShopItem.soldTo(role.get()), player)
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
        if (item == ShopItem.SPOOFER && equipment.hasSpoofer(player)) {
            player.sendMessage(messages.get("shop.spoofer-ready"));
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
        traitorChat = Set.of();
        equipment.stop();
        tester.reset();
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
        Bukkit.broadcast(won.map(faction -> messages.get("round.winner." + faction.key()))
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
                PlayerReset.reset(player, GameMode.ADVENTURE);
                player.teleport(town.lobbySpawn());
                hotbar.giveHub(player);
            }
        }
        corpses.clear();
        round.end();
        refreshAll();
    }

    public void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::show);
    }

    private void show(Player player) {
        var icons = GameSidebar.icons(player);
        var mapName = Component.text(town.name());
        var karmaSection = new GameSidebar.Section(messages.get("scoreboard.karma", icons),
                GameSidebar.value(Component.text(karma(player.getUniqueId()))));
        switch (phase()) {
            case LOBBY -> sidebar.lobby(player, config.capacity(), mapName, List.of(karmaSection));
            case INGAME -> {
                var sections = new ArrayList<GameSidebar.Section>();
                var role = roleOf(player);
                sections.add(new GameSidebar.Section(messages.get("scoreboard.role", icons), GameSidebar.value(role
                        .map(each -> each.displayName(messages)).orElseGet(() -> messages.get("scoreboard.no-role")))));
                if (role.filter(Role::hasShop).isPresent() && round.isPlaying(player)) {
                    sections.add(new GameSidebar.Section(messages.get("scoreboard.points", icons),
                            GameSidebar.value(Component.text(points(player)))));
                }
                if (role.filter(each -> each == Role.TRAITOR).isPresent()) {
                    sections.add(new GameSidebar.Section(messages.get("scoreboard.partners", icons), partners(player)));
                }
                sections.add(karmaSection);
                sidebar.round(player, config.capacity(), secondsLeft, mapName, sections);
            }
            case ENDING -> sidebar.end(player, winner.map(faction -> messages.get("scoreboard.winner." + faction.key())).stream().toList());
        }
    }

    /** The other traitors, dead or alive, which only a traitor sees. */
    private List<Component> partners(Player viewer) {
        var lines = new ArrayList<Component>();
        roles.forEach((id, role) -> {
            if (role != Role.TRAITOR || id.equals(viewer.getUniqueId())) {
                return;
            }
            var partner = Bukkit.getPlayer(id);
            var name = Placeholder.unparsed("player", names.getOrDefault(id, "?"));
            lines.add(messages.get(partner != null && round.isPlaying(partner) ? "scoreboard.partner-alive" : "scoreboard.partner-dead", name));
        });
        return lines.isEmpty() ? List.of(messages.get("scoreboard.partner-none")) : lines;
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
        corpses.clear();
        round.stop();
    }
}
