package me.blockcraft.story.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.BundledDefaults;
import me.blockcraft.api.game.Arena;
import me.blockcraft.api.game.GameConfig;
import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.game.LobbyCountdown;
import me.blockcraft.api.game.LobbyEntrance;
import me.blockcraft.api.game.LobbyHotbar;
import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.game.Round;
import me.blockcraft.api.game.RoundConnections;
import me.blockcraft.api.game.RoundSummary;
import me.blockcraft.api.game.StatsMenus;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.world.WaitingLobby;
import me.blockcraft.common.message.Messages;
import me.blockcraft.story.CoreDefaults;
import me.blockcraft.story.campaign.Campaign;
import me.blockcraft.story.campaign.Campaigns;
import me.blockcraft.story.campaign.Progress;
import me.blockcraft.story.campaign.Saves;
import me.blockcraft.story.cutscene.Cutscene;
import me.blockcraft.story.cutscene.Cutscenes;
import me.blockcraft.story.cutscene.SeenScenes;
import me.blockcraft.story.debug.Trace;
import me.blockcraft.story.dialogue.DialogueHud;
import me.blockcraft.story.dialogue.StorySound;
import me.blockcraft.story.dialogue.Voices;
import me.blockcraft.story.quest.HoldObjective;
import me.blockcraft.story.quest.Party;
import me.blockcraft.story.quest.Quests;
import me.blockcraft.story.quest.StoryNpcs;
import me.blockcraft.story.sound.Soundtrack;
import me.blockcraft.story.state.Facts;
import me.blockcraft.story.state.Timers;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One mission for a party of up to four, a chapter of it each round. The quest package drives it through the
 * {@link Stage}s; this class keeps the party's {@link Facts}, votes, timers, cutscenes and soundtrack, and lets
 * {@code /story} jump to any stage. Entering a stage saves the party's {@link Campaign} there, so a party that comes
 * back after its server stopped picks it up again at that stage, and the ending is saved before the round ends, which
 * opens the next of the {@link Chapters} to its members.
 */
public final class StoryGame implements RoundConnections.Seating, Party, Listener {

    /** The vote whose outcome the waiting lobby recalls from the party's last finished campaign. */
    static final String DECISION = "decision";
    static final String DONE = "done";
    /** The round's own summary title would cover the ending's at once. */
    private static final long ENDING_TITLE_TICKS = 100;
    /** How long the round waits beyond the title for the ending to be stored, as a database that hangs must not hold it. */
    private static final long ENDING_SAVE_SECONDS = 30;

    private final JavaPlugin plugin;
    private final Messages messages;
    private final ChapterWorlds worlds;
    private final Round round;
    private final RoundSummary summary;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final LobbyEntrance entrance;
    /** The world the party plays in now, with where it lands and rallies and its NPCs. */
    private Setting setting;
    private final LoadingScreen loading;
    private final Cutscenes scenes;
    private final StoryNpcs npcs;
    private final Consumer<Player> loadout;
    private final Hold hold;
    /** Each chapter's campaigns, in the chapters' order. */
    private final Map<String, Campaigns> campaigns = new LinkedHashMap<>();
    private final Function<String, Campaigns> campaignsOf;
    private final SeenScenes seen;
    private final BossBar bar;
    private final Voices voices;
    private final DialogueHud dialogue;
    private final Trace trace;
    private final Facts facts;
    private final Timers timers;
    private final Soundtrack soundtrack;
    private final Set<UUID> party = new LinkedHashSet<>();
    private final Map<String, Map<UUID, String>> votes = new HashMap<>();
    private final String mission;
    private final BukkitTask presence;
    /** The spots the party has held, by objective, which a member's stale objective must not restart. */
    private final Map<String, HoldObjective> held = new LinkedHashMap<>();
    private Saves saves;
    private Map<String, Cutscene> cutscenes;
    private List<Stage> stages;
    private Chapters chapters;
    /** The chapter this round plays, once the mission has started. */
    private Chapters.Chapter chapter;
    /** The chapter an operator picked in the waiting lobby, which then starts afresh whatever the members left open. */
    private Optional<Chapters.Chapter> picked = Optional.empty();
    private Quests quests;
    private boolean ended;
    /** The ending's save that the round's end waits for; a jump drops it, so an earlier ending cannot end a later run. */
    private CompletableFuture<Saves.Outcome> endingSaved;
    private boolean starting;

    /**
     * {@code campaignsOf} opens the campaigns stored under a chapter's key, and {@code loadout} equips a member
     * whenever the mission puts them on their feet: at its start and after a rejoin.
     */
    public StoryGame(JavaPlugin plugin, GameConfig gameConfig, Messages messages, ChapterWorlds worlds, StatsRepository stats,
                     StatsMenus statsMenus, Function<String, Campaigns> campaignsOf, SeenScenes seen, Consumer<Player> loadout) {
        this.plugin = plugin;
        this.loadout = loadout;
        this.messages = messages;
        this.worlds = worlds;
        this.campaignsOf = campaignsOf;
        this.trace = new Trace(plugin);
        this.facts = new Facts(trace);
        this.timers = new Timers(plugin, trace);
        var config = plugin.getConfig();
        var mission = config.getConfigurationSection("mission");
        this.mission = BundledDefaults.strict(mission).text("name");
        this.cutscenes = loadCutscenes(worlds.main(), config.getConfigurationSection("cutscenes.scenes"));
        this.stages = Stage.load(worlds.main(), config.getConfigurationSection("stages"));
        divide(chapters(this.mission, stages, config));
        chapters.all().forEach(each -> {
            worlds.check(each);
            Setting.check(positions(config, each));
        });
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(0, config.getInt("rewards.win")), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.getInt("players.max"), gameConfig);
        this.countdown = new LobbyCountdown(plugin, round, gameConfig, 1, ignored -> { }, this::start);
        this.hotbar = new LobbyHotbar(plugin, List.of(), statsMenus, countdown);
        this.entrance = new LobbyEntrance(round, countdown, hotbar, this::lobbySpawn, () -> Optional.of(setting.rally()),
                OptionalInt.empty(), this::greet);
        this.soundtrack = new Soundtrack(plugin, trace, Soundtrack.Settings.from(config.getConfigurationSection("soundtrack")), round::players);
        this.voices = Voices.load(plugin);
        warnLongVoices();
        this.seen = seen;
        var dialogueSettings = DialogueHud.Settings.from(config.getConfigurationSection("dialogue"),
                new DialogueHud.Settings.Hints(plain("dialogue.hint.advance"), plain("dialogue.hint.choose"), plain("dialogue.hint.close"),
                        speaker -> PlainTextComponentSerializer.plainText().serialize(
                                messages.get("dialogue.hint.watching", Placeholder.unparsed("player", speaker.getName())))));
        this.dialogue = new DialogueHud(plugin, dialogueSettings, round::players);
        this.scenes = new Cutscenes(plugin, messages, voices, seen, dialogueSettings.typing(), Cutscenes.Settings.from(config.getConfigurationSection("cutscenes")));
        this.npcs = new StoryNpcs(plugin);
        this.hold = new Hold(plugin, round::players);
        this.bar = BossBar.bossBar(Component.empty(), 1, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        this.setting = Setting.in(plugin, worlds.main(), mission, npcs, messages, round::players, dialogue);
        this.loading = new LoadingScreen(plugin, new LoadingScreen.Settings(Math.max(1, config.getInt("cutscenes.fade-ticks")),
                Math.max(0, config.getInt("travel.hold-ticks")), Math.max(1, config.getInt("travel.ready-timeout-seconds")) * 20));
        this.presence = Bukkit.getScheduler().runTaskTimer(plugin, () -> setting.tick(), 1, 1);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static Chapters chapters(String mission, List<Stage> stages, ConfigurationSection config) {
        return Chapters.load(mission, stages.stream().map(Stage::name).toList(), config.getConfigurationSection("chapters"));
    }

    /** Where a chapter's party lands, rallies and meets its NPCs: in its own section once it has its own world, else in {@code mission}. */
    private static ConfigurationSection positions(ConfigurationSection config, Chapters.Chapter chapter) {
        return config.getConfigurationSection(chapter.world().isPresent() ? "chapters." + chapter.name() : "mission");
    }

    private void divide(Chapters divided) {
        chapters = divided;
        divided.all().forEach(each -> campaigns.computeIfAbsent(each.name(), ignored -> campaignsOf.apply(each.campaignKey())));
    }

    /** A recording longer than its subtitle's slot runs into the next line or past the shot; its timing needs moving. */
    private void warnLongVoices() {
        cutscenes.values().forEach(cutscene -> cutscene.lines().forEach(line -> voices.of(line.text())
                .filter(voice -> voice.ticks() > line.ticks())
                .ifPresent(voice -> plugin.getSLF4JLogger().warn("The voice of {} lasts {} ticks, longer than its {} in cutscene {}",
                        line.text(), voice.ticks(), line.ticks(), cutscene.name()))));
    }

    private String plain(String key) {
        return PlainTextComponentSerializer.plainText().serialize(messages.get(key));
    }

    private static Map<String, Cutscene> loadCutscenes(World world, ConfigurationSection section) {
        return section.getKeys(false).stream().collect(Collectors.toMap(Function.identity(),
                name -> Cutscene.load(name, world, section.getConfigurationSection(name)), (a, b) -> a, LinkedHashMap::new));
    }

    /** The mission's name, which names its quest package and keys what its members have seen and decided. */
    public String mission() {
        return mission;
    }

    public StoryNpcs npcs() {
        return npcs;
    }

    public DialogueHud dialogue() {
        return dialogue;
    }

    public Voices voices() {
        return voices;
    }

    public Trace trace() {
        return trace;
    }

    public void connect(Quests quests) {
        this.quests = quests;
    }

    public Quests quests() {
        return quests;
    }

    public LobbyCountdown countdown() {
        return countdown;
    }

    public LobbyHotbar lobbyHotbar() {
        return hotbar;
    }

    public GamePhase phase() {
        return round.phase();
    }

    public List<Player> members() {
        return round.players();
    }

    public List<Stage> stages() {
        return stages;
    }

    public Chapters chapters() {
        return chapters;
    }

    /** The chapter this round plays; none in the waiting lobby. */
    public Optional<Chapters.Chapter> chapter() {
        return Optional.ofNullable(chapter);
    }

    /** The world the party plays in now, or would land in from the waiting lobby. */
    public World world() {
        return setting.world();
    }

    /** {@code /story chapter}: the next start plays {@code name} afresh, whatever the members have finished or left open. */
    public Optional<String> pick(String name) {
        if (phase() != GamePhase.LOBBY) return Optional.of("The round is " + phase() + "; a chapter is picked in the waiting lobby");
        picked = chapters.named(name);
        return picked.isPresent() ? Optional.empty() : Optional.of("No chapter " + name + "; there are " + chapters.all().stream().map(Chapters.Chapter::name).toList());
    }

    public Map<String, Cutscene> cutscenes() {
        return cutscenes;
    }

    public Timers timers() {
        return timers;
    }

    public Soundtrack music() {
        return soundtrack;
    }

    public Optional<Cutscenes.Progress> sceneProgress() {
        return scenes.progress();
    }

    /** How far the party has come with the spot it holds. */
    public Optional<Double> hold() {
        return hold.running() ? Optional.of(hold.progress()) : Optional.empty();
    }

    /** Each open vote's topic with every member's choice so far, by name. */
    public Map<String, Map<String, String>> openVotes() {
        var open = new LinkedHashMap<String, Map<String, String>>();
        votes.forEach((topic, cast) -> open.put(topic, cast.entrySet().stream().collect(Collectors.toMap(
                entry -> Optional.ofNullable(Bukkit.getOfflinePlayer(entry.getKey()).getName()).orElse(entry.getKey().toString()),
                Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new))));
        return open;
    }

    public Optional<Location> lobbySpawn() {
        return BlockcraftApi.get().waitingLobby().map(WaitingLobby::spawn).or(() -> Optional.of(setting.rally()));
    }

    private void greet(Player player) {
        seen.load(player);
        var id = player.getUniqueId();
        var stores = new LinkedHashMap<>(campaigns);
        Tasks.async(plugin, () -> Progress.of(id, stores), progress -> {
            progress.open().ifPresentOrElse(open -> player.sendMessage(messages.get("lobby.resume",
                            Placeholder.component("stage", stageName(open.campaign().stage())))),
                    () -> {
                        if (chapters.all().size() > 1) player.sendMessage(messages.get("lobby.chapter",
                                chapterTags(chapters.next(List.of(progress.finished().keySet())))));
                    });
            chapters.all().reversed().stream().map(each -> progress.finished().get(each.name())).filter(Objects::nonNull)
                    .findFirst().map(last -> last.facts().get(DECISION)).ifPresent(earlier ->
                            player.sendMessage(messages.get("lobby.previous", Placeholder.component("choice", messages.get("choice." + earlier)))));
        });
    }

    private Component stageName(String stage) {
        return messages.has("stage." + stage) ? messages.get("stage." + stage) : Component.text(stage);
    }

    private Component chapterName(Chapters.Chapter named) {
        return messages.has("chapter." + named.name()) ? messages.get("chapter." + named.name()) : Component.text(named.name());
    }

    private TagResolver chapterTags(Chapters.Chapter named) {
        return TagResolver.resolver(Placeholder.component("chapter", chapterName(named)), Formatter.number("number", chapters.number(named)));
    }

    @Override
    public void join(Player player) {
        if (round.rejoin(player)) {
            catchUp(player);
        } else {
            entrance.admit(player);
        }
    }

    /** A member back from a disconnect gets the party's state, not the one they left with. */
    private void catchUp(Player player) {
        saves.rejoin(player.getUniqueId()).exceptionally(failure -> {
            plugin.getSLF4JLogger().warn("Selecting campaign {} for returning member {} failed", saves.id(), player.getUniqueId(), failure);
            return null;
        });
        equip(player);
        player.teleport(setting.rally());
        player.showBossBar(bar);
        soundtrack.restart(player);
        var stage = stage();
        // A spot the party held while this member was away completes for them as well, its party actions acting no more.
        held.values().forEach(spot -> spot.completeFor(List.of(player)));
        if (!stage.isEmpty()) quests.runEach(List.of(player), "stage_" + stage, "rejoin");
        trace.event("member", player.getName() + " rejoined at " + stage);
        if (messages.has("stage." + stage)) {
            player.sendMessage(messages.get("rejoin.stage", Placeholder.component("stage", messages.get("stage." + stage))));
        }
        facts.get(DECISION).ifPresent(made -> player.sendMessage(messages.get("rejoin.choice", Placeholder.component("choice", messages.get("choice." + made)))));
    }

    @Override
    public void quit(Player player) {
        player.hideBossBar(bar);
        boolean gone = !round.stepOut(player, this::forfeited);
        if (gone) round.leave(player);
        trace.event("member", player.getName() + " left");
        // Bukkit still lists a player during their quit, and a decision must not run its actions as them.
        Tasks.sync(plugin, this::retally);
        // Without a place kept for rejoining, nobody comes back to end the round.
        if (gone && round.playerCount() == 0 && phase() == GamePhase.INGAME) round.end();
    }

    private void forfeited(UUID member) {
        if (round.playerCount() == 0 && phase() == GamePhase.INGAME) {
            round.end();
            return;
        }
        retally();
    }

    @Override
    public void vanished(Player player) {
        if (phase() == GamePhase.INGAME && round.isPlaying(player)) {
            round.leave(player);
            round.watch(player);
            if (round.playerCount() == 0) {
                round.end();
            } else {
                retally();
            }
        }
    }

    @Override
    public void refreshAll() {
    }

    /**
     * Looks up where the members stand when the countdown ends, as a lookup at their join may not have answered, then
     * resumes the first member's unfinished campaign, in the order they joined, or begins the party's next chapter. A
     * lookup that fails starts the countdown again rather than a new campaign that would bury the unfinished one.
     */
    private void start() {
        if (starting || phase() != GamePhase.LOBBY) return;
        starting = true;
        var members = round.players().stream().map(Player::getUniqueId).toList();
        var stores = new LinkedHashMap<>(campaigns);
        Tasks.async(plugin, () -> members.stream().map(member -> Progress.of(member, stores)).toList(), saved -> {
            starting = false;
            if (phase() != GamePhase.LOBBY || round.players().isEmpty()) return;
            if (!round.players().stream().map(Player::getUniqueId).toList().equals(members)) {
                countdown.start();
                return;
            }
            countdown.cancel();
            begin(saved);
        }, () -> {
            starting = false;
            Bukkit.broadcast(messages.get("campaign.unreadable"));
            countdown.start();
        });
    }

    /** {@code standings} are the members' in the order they joined, which is whose open campaign and whose ending count first. */
    private void begin(List<Progress> standings) {
        var open = picked.isPresent() ? Optional.<Progress.Open>empty() : standings.stream().map(Progress::open).flatMap(Optional::stream).findFirst();
        var resumed = open.flatMap(left -> chapters.named(left.chapter()))
                .filter(left -> left.stages().contains(open.get().campaign().stage()));
        if (open.isPresent() && resumed.isEmpty()) {
            trace.event("campaign", open.get().campaign().id() + " stopped at " + open.get().campaign().stage() + ", which is no stage of " + open.get().chapter() + " any more");
        }
        if (resumed.isPresent()) {
            var left = open.get().campaign();
            enterChapter(resumed.get(), left, "resume", () -> {
                Bukkit.broadcast(messages.get("campaign.resumed"));
                place(stage(left.stage()).orElseThrow(), left.facts(), "resume");
            });
            return;
        }
        var next = picked.orElseGet(() -> chapters.next(standings.stream().map(standing -> standing.finished().keySet()).toList()));
        var carried = chapters.before(next).flatMap(before -> standings.stream().map(standing -> standing.finished().get(before.name()))
                .filter(Objects::nonNull).findFirst()).map(ended -> carried(next, ended.facts())).orElse(Map.of());
        enterChapter(next, Campaign.fresh(), "start", () -> {
            if (chapters.all().size() > 1) Bukkit.broadcast(messages.get("campaign.chapter", chapterTags(next)));
            open(next, carried);
        });
    }

    /** The facts {@code next} takes over from {@code ended}, the facts the chapter before it ended with. */
    private static Map<String, String> carried(Chapters.Chapter next, Map<String, String> ended) {
        var carried = new LinkedHashMap<String, String>();
        next.carries().forEach(fact -> Optional.ofNullable(ended.get(fact)).ifPresent(value -> carried.put(fact, value)));
        return carried;
    }

    /** The chapter begins with what it carries over, by the package's {@code begin_<chapter>}, or {@code begin}. */
    private void open(Chapters.Chapter next, Map<String, String> carried) {
        carried.forEach((fact, value) -> facts.set(fact, value, "carried into " + next.name()));
        quests.runParty(quests.has("begin_" + next.name()) ? "begin_" + next.name() : "begin", "start of " + next.name());
    }

    /**
     * Makes {@code next}'s world ready, which may take a moment for a world of its own, then moves the party into it
     * with {@code campaign} and runs {@code then}. A world that cannot be loaded ends the round, or restarts the
     * waiting lobby's countdown.
     */
    private void enterChapter(Chapters.Chapter next, Campaign campaign, String cause, Runnable then) {
        trace.event("chapter", next.name() + " is getting ready (" + cause + ")");
        worlds.prepare(next).whenComplete((world, failure) -> Tasks.sync(plugin, () -> {
            if (failure != null || world == null) {
                plugin.getSLF4JLogger().warn("Chapter {} could not be loaded", next.name(), failure);
                trace.event("chapter", next.name() + " could not be loaded: " + (failure == null ? "no world" : failure.getMessage()));
                loading.clear(round.players());
                Bukkit.broadcast(messages.get("campaign.unreachable"));
                if (phase() == GamePhase.INGAME) round.end(); else countdown.start();
                return;
            }
            if (round.players().isEmpty()) return;
            var left = setting;
            if (!world.equals(left.world()) || next.world().isPresent() || chapter != null && chapter.world().isPresent()) {
                setting = Setting.in(plugin, world, positions(plugin.getConfig(), next), npcs, messages, round::players, dialogue);
                stages = Stage.load(world, plugin.getConfig().getConfigurationSection("stages"));
                cutscenes = loadCutscenes(world, plugin.getConfig().getConfigurationSection("cutscenes.scenes"));
            }
            assemble(next, campaign);
            if (left != setting) {
                round.spectators().forEach(watcher -> watcher.teleport(setting.rally()));
                left.close();
                trace.event("world", world.getKey().asString() + " (Bukkit name " + world.getName() + ") for " + next.name());
                if (!left.world().equals(world)) worlds.release(left.world());
            }
            then.run();
        }));
    }

    /** The members land, equipped, and the chapter begins, while the next chapter's world gets ready. */
    private void assemble(Chapters.Chapter played, Campaign campaign) {
        if (saves != null) saves.close();
        chapter = played;
        picked = Optional.empty();
        ended = false;
        endingSaved = null;
        facts.set(Facts.CHAPTER, played.name(), "start");
        saves = new Saves(campaigns.get(played.name()), campaign, plugin.getSLF4JLogger());
        trace.event("campaign", campaign.id() + (campaign.revision() == 0 ? " begins " + played.name()
                : " resumes " + played.name() + " from revision " + campaign.revision()));
        if (phase() == GamePhase.LOBBY) round.enter(GamePhase.INGAME);
        setting.dress();
        var members = round.players();
        for (int i = 0; i < members.size(); i++) {
            var member = members.get(i);
            party.add(member.getUniqueId());
            equip(member);
            member.teleport(setting.landing(i));
            member.showBossBar(bar);
        }
        trace.event("mission", played.name() + " with " + members.stream().map(Player::getName).collect(Collectors.joining(", ")));
        chapters.after(played).ifPresent(worlds::prepare);
    }

    private void equip(Player player) {
        PlayerReset.reset(player, GameMode.ADVENTURE);
        loadout.accept(player);
    }

    private Optional<Stage> stage(String name) {
        return stages.stream().filter(stage -> stage.name().equals(name)).findFirst();
    }

    @Override
    public Facts facts() {
        return facts;
    }

    @Override
    public boolean playing(UUID player) {
        var online = Bukkit.getPlayer(player);
        return online != null && round.isPlaying(online);
    }

    @Override
    public String stage() {
        return facts.get(Facts.STAGE).orElse("");
    }

    @Override
    public boolean voted(UUID player, String topic) {
        return votes.getOrDefault(topic, Map.of()).containsKey(player);
    }

    @Override
    public void advance(String name, String cause) {
        if (phase() != GamePhase.INGAME || ended) return;
        var next = stage(name);
        if (next.isEmpty()) {
            trace.event("missing", "stage " + name + " by " + cause);
            return;
        }
        if (!chapter.stages().contains(name)) {
            trace.event("stage", name + " ignored, it is no stage of " + chapter.name() + " (" + cause + ")");
            return;
        }
        var current = stage(stage());
        if (current.isPresent() && next.get().order() <= current.get().order()) {
            trace.event("stage", name + " ignored, the party is at " + stage() + " (" + cause + ")");
            return;
        }
        enter(next.get(), cause);
    }

    /**
     * The party enters {@code stage}: the boss bar, its line and sound, its soundtrack, then the package's
     * {@code stage_<stage>} for every member and {@code enter_<stage>} once.
     */
    private void enter(Stage stage, String cause) {
        facts.set(Facts.CHAPTER, chapter.name(), cause);
        facts.set(Facts.STAGE, stage.name(), cause);
        save(Optional.empty(), "stage " + stage.name(), ignored -> { });
        quests.nextStage();
        if (messages.has("stage." + stage.name())) bar.name(messages.get("stage." + stage.name()));
        bar.progress(Math.clamp(stage.progress(), 0, 1));
        bar.color(stage.bar());
        if (messages.has("stage-start." + stage.name())) Bukkit.broadcast(messages.get("stage-start." + stage.name()));
        var cue = Sound.sound(Key.key("blockcraft", "story." + stage.cue()), Sound.Source.MASTER, 0.8f, 1);
        round.players().forEach(member -> member.playSound(cue, Sound.Emitter.self()));
        stage.music().ifPresent(track -> soundtrack(Soundtrack.Channel.MUSIC, track, "stage " + stage.name()));
        stage.ambience().ifPresent(track -> soundtrack(Soundtrack.Channel.AMBIENCE, track, "stage " + stage.name()));
        quests.runEach(round.players(), "stage_" + stage.name(), "stage " + stage.name());
        quests.runParty("enter_" + stage.name(), "stage " + stage.name());
    }

    /**
     * Puts the party at {@code name} as if it had played up to there: whatever runs is stopped, the facts become the
     * stage's own with {@code overrides} on top, and the members stand at its spawn. A stage of another chapter, or any
     * stage from the waiting lobby, starts that chapter first, in its world, without what begins it.
     */
    public Optional<String> jump(String name, Map<String, String> overrides, String cause) {
        var target = chapters.of(name);
        if (target.isEmpty()) return Optional.of("No stage " + name + "; there are " + stages.stream().map(Stage::name).toList());
        if (phase() == GamePhase.LOBBY) {
            if (round.players().isEmpty()) return Optional.of("Nobody is in the waiting lobby to play it");
            countdown.cancel();
        } else if (phase() != GamePhase.INGAME) {
            return Optional.of("The round is " + phase() + ", wait for the next one");
        }
        trace.event("jump", name + " " + overrides + " by " + cause);
        Runnable land = () -> {
            var stage = stage(name).orElseThrow();
            var start = new LinkedHashMap<>(stage.facts());
            start.putAll(overrides);
            place(stage, start, cause);
        };
        if (phase() == GamePhase.INGAME && target.get().equals(chapter)) {
            land.run();
        } else {
            halt(cause);
            loading.clear(round.players());
            enterChapter(target.get(), Campaign.fresh(), cause, land);
        }
        return Optional.empty();
    }

    /** Stops whatever runs: scenes, soundtrack, timers, a hold, votes, conversations and a pending ending. */
    private void halt(String cause) {
        scenes.cancelAll();
        scenes.restore();
        soundtrack.stopAll();
        timers.clear(cause);
        hold.close();
        votes.clear();
        held.clear();
        quests.forget();
        ended = false;
        endingSaved = null;
        round.players().forEach(dialogue::close);
        hold(false);
    }

    /** Stops whatever runs and puts the party at {@code target} with exactly {@code start} as its facts. */
    private void place(Stage target, Map<String, String> start, String cause) {
        halt(cause);
        facts.clearAll(cause);
        facts.set(Facts.CHAPTER, chapter.name(), cause);
        start.forEach((fact, value) -> facts.set(fact, value, cause));
        var spawn = target.spawn().orElse(setting.rally());
        round.players().forEach(member -> {
            member.setGameMode(GameMode.ADVENTURE);
            member.teleport(spawn);
            member.showBossBar(bar);
        });
        enter(target, cause);
    }

    @Override
    public void vote(Player voter, String topic, String choice) {
        if (phase() != GamePhase.INGAME || facts.get(topic).isPresent() || !round.isPlaying(voter)) return;
        var cast = votes.computeIfAbsent(topic, ignored -> new HashMap<>());
        cast.put(voter.getUniqueId(), choice);
        trace.event("vote", voter.getName() + " " + topic + " = " + choice);
        Bukkit.broadcast(messages.get("vote.cast", Placeholder.unparsed("player", voter.getName()),
                Placeholder.component("choice", messages.get("choice." + choice)),
                Formatter.number("votes", cast.size()), Formatter.number("members", round.playerCount())));
        round.players().forEach(StorySound.VOTE_CAST::play);
        tally(topic);
    }

    /** Decides the vote once every member who is here has voted, as {@link Vote#outcome} counts it. */
    private void tally(String topic) {
        var cast = votes.get(topic);
        if (cast == null || facts.get(topic).isPresent()) return;
        Vote.outcome(cast, round.players().stream().map(Player::getUniqueId).toList(), party).ifPresent(decided -> decide(topic, decided));
    }

    /** Fewer members are here, so an open vote may be decided by those who are. */
    private void retally() {
        if (phase() == GamePhase.INGAME) List.copyOf(votes.keySet()).forEach(this::tally);
    }

    private void decide(String topic, String decided) {
        votes.remove(topic);
        facts.set(topic, decided, "vote");
        Bukkit.broadcast(messages.get("vote.decided", Placeholder.component("choice", messages.get("choice." + decided))));
        round.players().forEach(StorySound.VOTE_DECIDED::play);
        quests.runParty("decided_" + topic, "vote " + topic);
    }

    @Override
    public void scene(String name, List<String> then, String cause) {
        if (scenes.partyPlaying()) {
            // Each member who reaches the same trigger asks again; the scene already playing is the party's.
            trace.event("scene", name + " ignored, the party's scene is still playing (" + cause + ")");
            return;
        }
        var cutscene = cutscenes.get(name);
        if (cutscene == null) {
            trace.event("missing", "scene " + name + " by " + cause);
            then.forEach(action -> quests.runParty(action, "scene " + name));
            return;
        }
        play(cutscene, round.players(), true, cause, () -> then.forEach(action -> quests.runParty(action, "scene " + name)));
    }

    /** Plays {@code name} from {@code start} to {@code viewers} alone, as {@code /story scene} does; the story does not go on. */
    public Optional<String> preview(String name, int start, List<Player> viewers) {
        var cutscene = cutscenes.get(name);
        if (cutscene == null) return Optional.of("No scene " + name + "; there are " + cutscenes.keySet());
        if (scenes.progress().isPresent()) return Optional.of("A scene is playing; /story skip or /story scene stop first");
        try {
            play(cutscene.from(start), viewers, false, "preview from " + start, () -> { });
        } catch (IllegalArgumentException invalid) {
            return Optional.of(invalid.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Plays a scene. The party's scene starts its music on the soundtrack and runs its actions; a preview keeps its
     * music to its viewers and only names its actions in the trace, so the party's story stays as it was.
     */
    private void play(Cutscene cutscene, List<Player> viewers, boolean forParty, String cause, Runnable then) {
        viewers.forEach(dialogue::close);
        hold(true);
        trace.event("scene", cutscene.name() + " starts, " + cutscene.duration() + " ticks, for " + viewers.size() + " (" + cause + ")");
        var music = cutscene.music().map(track -> Sound.sound(Key.key("blockcraft", "story.music." + track), Sound.Source.MUSIC, 0.5f, 1));
        if (forParty) {
            cutscene.music().ifPresent(track -> soundtrack(Soundtrack.Channel.MUSIC, track, "scene " + cutscene.name()));
        } else {
            music.ifPresent(sound -> viewers.forEach(viewer -> viewer.playSound(sound, Sound.Emitter.self())));
        }
        Consumer<String> run = forParty ? action -> quests.runParty(action, "scene " + cutscene.name())
                : action -> trace.event("action", action + " skipped in the preview");
        scenes.play(cutscene, viewers, forParty, setting.cast(), run, () -> {
            hold(false);
            if (!forParty) music.ifPresent(sound -> viewers.forEach(viewer -> viewer.stopSound(sound)));
            viewers.forEach(soundtrack::restart);
            trace.event("scene", cutscene.name() + " ended");
            then.run();
        });
    }

    /** Ends the previews; the party's own scene goes on, as {@code /story skip} ends it with the story going on. */
    public void stopPreviews() {
        scenes.cancelPreviews();
        if (!scenes.partyPlaying()) hold(false);
    }

    /** A cutscene moves the NPCs while it plays, so they leave their bearing to it. */
    private void hold(boolean held) {
        setting.hold(held);
    }

    /** {@code /story skip}: whatever the party waits on now ends at once, the scene first, then the hold, then a timer. */
    public String skip() {
        if (scenes.progress().isPresent()) {
            scenes.skipAll();
            return "scene";
        }
        if (hold.running() && !round.players().isEmpty()) {
            hold.finishNow();
            return "hold";
        }
        return timers.fireNext() ? "timer" : "";
    }

    @Override
    public void after(int ticks, List<String> actions, String cause) {
        var key = String.join(",", actions);
        if (timers.waiting(key)) {
            // Each member who reaches the same trigger asks again; one timer serves the party.
            trace.event("timer", key + " already waits, ignored (" + cause + ")");
            return;
        }
        timers.after(ticks, key, key + " (" + cause + ")", () -> actions.forEach(action -> quests.runParty(action, "timer")));
    }

    @Override
    public void soundtrack(Soundtrack.Channel channel, String track, String cause) {
        if (track.equals("stop")) {
            soundtrack.stop(channel);
        } else {
            soundtrack.play(channel, track);
        }
    }

    @Override
    public void sound(Key sound, float volume, float pitch, Optional<Location> at) {
        var played = Sound.sound(sound, Sound.Source.MASTER, volume, pitch);
        trace.event("sound", sound.asString() + at.map(where -> " at " + where.toVector()).orElse(""));
        round.players().forEach(member -> at.ifPresentOrElse(where -> member.playSound(played, where.getX(), where.getY(), where.getZ()),
                () -> member.playSound(played, Sound.Emitter.self())));
    }

    @Override
    public void holdReached(HoldObjective spot, Player member) {
        if (phase() != GamePhase.INGAME || ended || hold.running() || !round.isPlaying(member) || held.containsKey(spot.id())) return;
        boolean started = hold.start(spot, progress -> bar.progress((float) progress), () -> {
            held.put(spot.id(), spot);
            trace.event("hold", spot.id() + " held");
        });
        if (!started) return;
        trace.event("hold", spot.id() + " started by " + member.getName());
        round.players().forEach(StorySound.OBJECTIVE_START::play);
    }

    @Override
    public void end(String ending, String cause) {
        if (phase() != GamePhase.INGAME || ended) return;
        ended = true;
        facts.set(Facts.STAGE, DONE, cause);
        timers.clear("end");
        var title = Title.title(messages.get("ending." + ending + ".title"), messages.get("ending." + ending + ".subtitle"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(800)));
        var sting = Sound.sound(Key.key("blockcraft", "story.ending." + ending.toLowerCase(Locale.ROOT)), Sound.Source.MASTER, 1, 1);
        for (var member : round.players()) {
            member.hideBossBar(bar);
            member.showTitle(title);
            member.playSound(sting, Sound.Emitter.self());
            summary.awardWin(member);
        }
        Bukkit.broadcast(messages.get("ending." + ending + ".summary"));
        trace.event("mission", "ended: " + ending);
        var saved = new CompletableFuture<Saves.Outcome>();
        endingSaved = saved;
        save(Optional.of(ending), "ending " + ending, saved::complete);
        var next = chapters.after(chapter);
        next.ifPresent(worlds::prepare);
        // Once the title has shown and the ending is stored, the party goes on to the next chapter, or the round ends,
        // as the next round's server starts fresh.
        Tasks.later(plugin, ENDING_TITLE_TICKS, () -> saved.completeOnTimeout(Saves.Outcome.FAILED, ENDING_SAVE_SECONDS, TimeUnit.SECONDS)
                .thenAccept(outcome -> Tasks.sync(plugin, () -> {
                    // A /story jump in the meantime has started the mission again.
                    if (endingSaved != saved) return;
                    if (outcome != Saves.Outcome.SAVED) Bukkit.broadcast(messages.get("campaign.unsaved"));
                    if (next.isPresent() && !round.players().isEmpty()) {
                        travel(next.get());
                        return;
                    }
                    scenes.restore();
                    soundtrack.stopAll();
                    round.end();
                })));
    }

    /**
     * The party goes on to {@code next} in the same round: the screen goes black under the chapter's name, the world it
     * plays in gets ready, the members land there, and the screen comes back once their clients have loaded it.
     */
    private void travel(Chapters.Chapter next) {
        var carried = carried(next, facts.all());
        trace.event("chapter", chapter.name() + " is over, on to " + next.name());
        loading.cover(round::players, chapterName(next), messages.get("travel.loading", chapterTags(next)), () -> {
            halt("travel");
            facts.clearAll("travel");
            enterChapter(next, Campaign.fresh(), "travel", () -> loading.reveal(round::players, () -> open(next, carried)));
        });
    }

    /** Saves the party's facts as they are now, and with {@code ending} once it has one, then hands back how it went. */
    private void save(Optional<String> ending, String what, Consumer<Saves.Outcome> then) {
        var saving = saves;
        saving.save(facts.all(), ending, party).whenComplete((outcome, failure) -> Tasks.sync(plugin, () -> {
            if (failure != null) plugin.getSLF4JLogger().warn("Saving campaign {} failed", saving.id(), failure);
            var result = failure == null ? outcome : Saves.Outcome.FAILED;
            trace.event("campaign", what + (result == Saves.Outcome.SAVED ? " saved as revision " + saving.revision() : " not saved: " + result));
            then.accept(result);
        }));
    }

    /** The campaign the party plays, for {@code /story state}. */
    public Optional<String> campaign() {
        return Optional.ofNullable(saves).map(saving -> saving.id() + " at revision " + saving.revision());
    }

    /**
     * Reads config.yml again, with its scenes, stages and soundtrack; what is playing keeps its old version. A file that
     * does not parse changes nothing, and the error names what is wrong.
     */
    public String reload() {
        CoreDefaults.underConfig(plugin);
        var config = plugin.getConfig();
        Map<String, Cutscene> newScenes;
        List<Stage> newStages;
        Chapters newChapters;
        Soundtrack.Settings newSoundtrack;
        try {
            newScenes = loadCutscenes(setting.world(), config.getConfigurationSection("cutscenes.scenes"));
            newStages = Stage.load(setting.world(), config.getConfigurationSection("stages"));
            newChapters = chapters(mission, newStages, config);
            newSoundtrack = Soundtrack.Settings.from(config.getConfigurationSection("soundtrack"));
        } catch (RuntimeException invalid) {
            trace.event("reload", "failed: " + invalid.getMessage());
            return "nothing, config.yml has an error: " + invalid.getMessage();
        }
        if (chapter != null && newChapters.named(chapter.name()).isEmpty()) {
            return "nothing, the round plays " + chapter.name() + ", which config.yml no longer has";
        }
        cutscenes = newScenes;
        stages = newStages;
        divide(newChapters);
        if (chapter != null) chapter = newChapters.named(chapter.name()).orElseThrow();
        soundtrack.settings(newSoundtrack);
        warnLongVoices();
        var loaded = cutscenes.size() + " scenes, " + stages.size() + " stages, " + chapters.all().size() + " chapters";
        trace.event("reload", loaded);
        return loaded;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Hanging || event.getEntity() instanceof ArmorStand) {
            if (!(event.getDamageSource().getCausingEntity() instanceof Player builder
                    && BlockcraftApi.get().buildMode().isEnabled(builder))) event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof Player member)) return;
        if (phase() != GamePhase.INGAME || !round.isPlaying(member)) {
            event.setCancelled(true);
            return;
        }
        if (member.getHealth() - event.getFinalDamage() > 0) return;
        event.setCancelled(true);
        PlayerReset.resetHealth(member);
        member.teleport(setting.rally());
        member.sendMessage(messages.get("fallen"));
        trace.event("member", member.getName() + " fell and is back at the rally");
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    public void stop() {
        countdown.cancel();
        hold.close();
        presence.cancel();
        scenes.close();
        dialogue.close();
        timers.clear("shutdown");
        soundtrack.close();
        if (saves != null) saves.close();
        loading.close();
        setting.close();
        worlds.close();
        round.stop();
    }
}
