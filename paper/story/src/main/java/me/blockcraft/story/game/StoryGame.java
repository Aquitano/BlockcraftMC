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
import me.blockcraft.story.campaign.Campaign;
import me.blockcraft.story.campaign.Campaigns;
import me.blockcraft.story.campaign.Saves;
import me.blockcraft.story.cutscene.Cutscene;
import me.blockcraft.story.cutscene.Cutscenes;
import me.blockcraft.story.cutscene.SeenScenes;
import me.blockcraft.story.debug.Trace;
import me.blockcraft.story.dialogue.DialogueHud;
import me.blockcraft.story.dialogue.StorySound;
import me.blockcraft.story.dialogue.Voices;
import me.blockcraft.story.quest.ConsoleObjective;
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
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * One mission for a party of up to four. The quest package drives it through the {@link Stage}s; this class keeps the
 * party's {@link Facts}, votes, timers, cutscenes and soundtrack, and lets {@code /story} jump to any stage. Entering a
 * stage saves the party's {@link Campaign} there, so a party that comes back after its server stopped picks it up again
 * at that stage, and the ending is saved before the round ends.
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
    private final World world;
    private final Round round;
    private final RoundSummary summary;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final LobbyEntrance entrance;
    private final List<Location> landing;
    private final Location rally;
    private final Cutscenes scenes;
    private final StoryNpcs npcs;
    private final Blasters blasters;
    private final ConsoleDefense defense;
    private final Campaigns campaigns;
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
    private final Map<String, String> npcModels = new LinkedHashMap<>();
    private final List<NpcPresence> presences = new ArrayList<>();
    private final BukkitTask presence;
    /** The consoles the party has held, by objective, which a member's stale objective must not restart. */
    private final Map<String, ConsoleObjective> heldConsoles = new LinkedHashMap<>();
    private Saves saves;
    private Map<String, Cutscene> cutscenes;
    private List<Stage> stages;
    private Quests quests;
    private boolean ended;
    /** The ending's save that the round's end waits for; a jump drops it, so an earlier ending cannot end a later run. */
    private CompletableFuture<Saves.Outcome> endingSaved;
    private boolean starting;

    public StoryGame(JavaPlugin plugin, GameConfig gameConfig, Messages messages, World world, StatsRepository stats,
                     StatsMenus statsMenus, Campaigns campaigns, SeenScenes seen) {
        this.plugin = plugin;
        this.messages = messages;
        this.world = world;
        this.campaigns = campaigns;
        this.trace = new Trace(plugin);
        this.facts = new Facts(trace);
        this.timers = new Timers(plugin, trace);
        var config = plugin.getConfig();
        var mission = config.getConfigurationSection("mission");
        this.mission = mission.getString("name", "mission");
        this.landing = Spots.all(world, mission, "landing");
        this.rally = Spots.at(world, mission, "rally");
        this.cutscenes = loadCutscenes(world, config.getConfigurationSection("cutscenes.scenes"));
        this.stages = Stage.load(world, config.getConfigurationSection("stages"));
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(0, config.getInt("rewards.win")), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.getInt("players.max"), gameConfig);
        this.countdown = new LobbyCountdown(plugin, round, gameConfig, 1, ignored -> { }, this::start);
        this.hotbar = new LobbyHotbar(plugin, List.of(), statsMenus, countdown);
        this.entrance = new LobbyEntrance(round, countdown, hotbar, this::lobbySpawn, () -> Optional.of(rally),
                OptionalInt.empty(), this::greet);
        this.soundtrack = new Soundtrack(plugin, trace, Soundtrack.Settings.from(config.getConfigurationSection("soundtrack")), round::players);
        this.voices = Voices.load();
        warnLongVoices();
        this.seen = seen;
        var dialogueSettings = DialogueHud.Settings.from(config.getConfigurationSection("dialogue"),
                new DialogueHud.Settings.Hints(plain("dialogue.hint.advance"), plain("dialogue.hint.choose"), plain("dialogue.hint.close"),
                        speaker -> PlainTextComponentSerializer.plainText().serialize(
                                messages.get("dialogue.hint.watching", Placeholder.unparsed("player", speaker.getName())))));
        this.dialogue = new DialogueHud(plugin, dialogueSettings, round::players);
        this.scenes = new Cutscenes(plugin, messages, voices, seen, dialogueSettings.typing(), Cutscenes.Settings.from(config.getConfigurationSection("cutscenes")));
        this.npcs = new StoryNpcs(plugin);
        this.blasters = new Blasters(plugin, config.getDouble("blaster.damage"), config.getInt("blaster.cooldown-ticks"),
                config.getDouble("blaster.range"));
        this.defense = new ConsoleDefense(plugin, ConsoleDefense.Settings.from(config.getConfigurationSection("defense")),
                Spots.all(world, mission, "droid-spawns"), round::players, blasters, messages.get("defense.droid"));
        this.bar = BossBar.bossBar(Component.empty(), 1, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        var npcSection = mission.getConfigurationSection("npcs");
        if (npcSection != null) {
            for (var id : npcSection.getKeys(false)) {
                var npc = npcSection.getConfigurationSection(id);
                var post = Spots.at(world, npc, "at");
                npcModels.put(id, npc.getString("model", ""));
                presences.add(new NpcPresence(id, spawnNpc(id, post), post, round::players, dialogue));
            }
        }
        this.presence = Bukkit.getScheduler().runTaskTimer(plugin, () -> presences.forEach(NpcPresence::tick), 1, 1);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(blasters, plugin);
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

    /** An NPC from {@code mission.npcs}, named by {@code npc.<id>.name} and {@code npc.<id>.role} in messages.yml. */
    private Mannequin spawnNpc(String id, Location at) {
        world.addPluginChunkTicket(at.getBlockX() >> 4, at.getBlockZ() >> 4, plugin);
        var npc = world.spawn(at, Mannequin.class, entity -> {
            entity.setPersistent(false);
            entity.setImmovable(true);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.customName(messages.get("npc." + id + ".name"));
            entity.setCustomNameVisible(true);
            entity.setDescription(messages.get("npc." + id + ".role"));
        });
        npcs.put(id, npc);
        return npc;
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

    /** How far the console's upload has come while the defence runs. */
    public Optional<Double> upload() {
        return defense.running() ? Optional.of(defense.progress()) : Optional.empty();
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
        return BlockcraftApi.get().waitingLobby().map(WaitingLobby::spawn).or(() -> Optional.of(rally));
    }

    private void greet(Player player) {
        seen.load(player);
        var id = player.getUniqueId();
        Tasks.async(plugin, () -> campaigns.unfinished(id), unfinished -> unfinished.ifPresent(campaign ->
                player.sendMessage(messages.get("lobby.resume", Placeholder.component("stage", stageName(campaign.stage()))))));
        Tasks.async(plugin, () -> campaigns.finished(id).flatMap(campaign -> Optional.ofNullable(campaign.facts().get(DECISION))),
                previous -> previous.ifPresent(earlier ->
                        player.sendMessage(messages.get("lobby.previous", Placeholder.component("choice", messages.get("choice." + earlier))))));
    }

    private Component stageName(String stage) {
        return messages.has("stage." + stage) ? messages.get("stage." + stage) : Component.text(stage);
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
        player.teleport(rally);
        player.showBossBar(bar);
        soundtrack.restart(player);
        var stage = stage();
        // A console the party held while this member was away completes for them as well, its party actions acting no more.
        heldConsoles.values().forEach(console -> console.completeFor(List.of(player)));
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
     * Looks up the members' campaigns when the countdown ends, as a lookup at their join may not have answered, then
     * resumes the first member's unfinished one, in the order they joined, or begins a new one. A lookup that fails
     * starts the countdown again rather than a new campaign that would bury the unfinished one.
     */
    private void start() {
        if (starting || phase() != GamePhase.LOBBY) return;
        starting = true;
        var members = round.players().stream().map(Player::getUniqueId).toList();
        Tasks.async(plugin, () -> members.stream().map(campaigns::unfinished).flatMap(Optional::stream).findFirst(), saved -> {
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

    private void begin(Optional<Campaign> saved) {
        var at = saved.flatMap(campaign -> stage(campaign.stage()));
        if (saved.isPresent() && at.isEmpty()) trace.event("campaign", saved.get().id() + " stopped at " + saved.get().stage() + ", which is no stage any more");
        assemble(at.isPresent() ? saved.get() : Campaign.fresh());
        if (at.isPresent()) {
            Bukkit.broadcast(messages.get("campaign.resumed"));
            place(at.get(), saved.get().facts(), "resume");
        } else {
            quests.runParty("begin", "mission start");
        }
    }

    /** The members land, armed, and the mission begins; the NPCs get their bodies now, as BetterModel loads after the plugins. */
    private void assemble(Campaign campaign) {
        if (saves != null) saves.close();
        saves = new Saves(campaigns, campaign, plugin.getSLF4JLogger());
        trace.event("campaign", campaign.id() + (campaign.revision() == 0 ? " begins" : " resumes from revision " + campaign.revision()));
        round.enter(GamePhase.INGAME);
        presences.forEach(npc -> npc.dress(npcModels.getOrDefault(npc.id(), "")));
        var members = round.players();
        for (int i = 0; i < members.size(); i++) {
            var member = members.get(i);
            party.add(member.getUniqueId());
            equip(member);
            member.teleport(landing.isEmpty() ? rally : landing.get(i % landing.size()));
            member.showBossBar(bar);
        }
        trace.event("mission", "started with " + members.stream().map(Player::getName).collect(Collectors.joining(", ")));
    }

    private void equip(Player player) {
        PlayerReset.reset(player, GameMode.ADVENTURE);
        player.getInventory().setItem(0, blasters.item(messages.get("item.blaster")));
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
     * stage's own with {@code overrides} on top, and the members stand at its spawn. From the waiting lobby it starts
     * the mission first, without the intro.
     */
    public Optional<String> jump(String name, Map<String, String> overrides, String cause) {
        var target = stage(name);
        if (target.isEmpty()) return Optional.of("No stage " + name + "; there are " + stages.stream().map(Stage::name).toList());
        if (phase() == GamePhase.LOBBY) {
            if (round.players().isEmpty()) return Optional.of("Nobody is in the waiting lobby to play it");
            countdown.cancel();
            assemble(Campaign.fresh());
        } else if (phase() != GamePhase.INGAME) {
            return Optional.of("The round is " + phase() + ", wait for the next one");
        }
        trace.event("jump", name + " " + overrides + " by " + cause);
        var start = new LinkedHashMap<>(target.get().facts());
        start.putAll(overrides);
        place(target.get(), start, cause);
        return Optional.empty();
    }

    /** Stops whatever runs and puts the party at {@code target} with exactly {@code start} as its facts. */
    private void place(Stage target, Map<String, String> start, String cause) {
        scenes.cancelAll();
        scenes.restore();
        soundtrack.stopAll();
        timers.clear("jump");
        defense.close();
        votes.clear();
        heldConsoles.clear();
        quests.forget();
        ended = false;
        endingSaved = null;
        round.players().forEach(dialogue::close);
        hold(false);
        facts.clearAll(cause);
        start.forEach((fact, value) -> facts.set(fact, value, cause));
        var spawn = target.spawn().orElse(rally);
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
        var cast = new HashMap<String, Cutscenes.Npc>();
        npcs.all().forEach((id, body) -> cast.put(id, new Cutscenes.Npc(body, npcModels.getOrDefault(id, ""))));
        scenes.play(cutscene, viewers, forParty, cast, run, () -> {
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
        presences.forEach(npc -> npc.hold(held));
    }

    /** {@code /story skip}: whatever the party waits on now ends at once, the scene first, then the upload, then a timer. */
    public String skip() {
        if (scenes.progress().isPresent()) {
            scenes.skipAll();
            return "scene";
        }
        if (defense.running() && !round.players().isEmpty()) {
            defense.finishNow();
            return "upload";
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
    public void consoleReached(ConsoleObjective console, Player member) {
        if (phase() != GamePhase.INGAME || ended || defense.running() || !round.isPlaying(member) || heldConsoles.containsKey(console.id())) return;
        boolean started = defense.start(console, progress -> bar.progress((float) progress), () -> {
            heldConsoles.put(console.id(), console);
            trace.event("defense", "held");
            Bukkit.broadcast(messages.get("defense.held"));
        });
        if (!started) return;
        trace.event("defense", "started");
        Bukkit.broadcast(messages.get("defense.started"));
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
        // The round ends once the title has shown and the ending is stored, as the next round's server starts fresh.
        Tasks.later(plugin, ENDING_TITLE_TICKS, () -> saved.completeOnTimeout(Saves.Outcome.FAILED, ENDING_SAVE_SECONDS, TimeUnit.SECONDS)
                .thenAccept(outcome -> Tasks.sync(plugin, () -> {
                    // A /story jump in the meantime has started the mission again.
                    if (endingSaved != saved) return;
                    if (outcome != Saves.Outcome.SAVED) Bukkit.broadcast(messages.get("campaign.unsaved"));
                    scenes.restore();
                    soundtrack.stopAll();
                    round.end();
                })));
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
        plugin.reloadConfig();
        BundledDefaults.apply(plugin);
        var config = plugin.getConfig();
        Map<String, Cutscene> newScenes;
        List<Stage> newStages;
        Soundtrack.Settings newSoundtrack;
        try {
            newScenes = loadCutscenes(world, config.getConfigurationSection("cutscenes.scenes"));
            newStages = Stage.load(world, config.getConfigurationSection("stages"));
            newSoundtrack = Soundtrack.Settings.from(config.getConfigurationSection("soundtrack"));
        } catch (RuntimeException invalid) {
            trace.event("reload", "failed: " + invalid.getMessage());
            return "nothing, config.yml has an error: " + invalid.getMessage();
        }
        cutscenes = newScenes;
        stages = newStages;
        soundtrack.settings(newSoundtrack);
        warnLongVoices();
        var loaded = cutscenes.size() + " scenes, " + stages.size() + " stages";
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
        member.teleport(rally);
        member.sendMessage(messages.get("defense.down"));
        trace.event("member", member.getName() + " fell and is back at the rally");
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    public void stop() {
        countdown.cancel();
        defense.close();
        presence.cancel();
        scenes.close();
        dialogue.close();
        timers.clear("shutdown");
        soundtrack.close();
        if (saves != null) saves.close();
        npcs.all().values().forEach(Mannequin::remove);
        round.stop();
    }
}
