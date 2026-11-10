package me.blockcraft.story.game;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.blockcraft.api.BlockcraftApi;
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
import me.blockcraft.story.cutscene.Cutscene;
import me.blockcraft.story.cutscene.Cutscenes;
import me.blockcraft.story.cutscene.SeenScenes;
import me.blockcraft.story.dialogue.DialogueHud;
import me.blockcraft.story.dialogue.StorySound;
import me.blockcraft.story.dialogue.Voices;
import me.blockcraft.story.quest.ConsoleObjective;
import me.blockcraft.story.quest.Party;
import me.blockcraft.story.quest.Quests;
import me.blockcraft.story.quest.StoryNpcs;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One mission for a party of up to four. The party moves through the {@link Stage}s together and decides together;
 * BetonQuest runs the conversations and each member's objectives, and asks this class for the shared state.
 */
public final class StoryGame implements RoundConnections.Seating, Party, Listener {

    public static final String MISSION = "tessar";
    /** The round's own summary title would cover the ending's at once. */
    private static final long ENDING_TITLE_TICKS = 100;

    private final Plugin plugin;
    private final Messages messages;
    private final World world;
    private final Round round;
    private final RoundSummary summary;
    private final LobbyCountdown countdown;
    private final LobbyHotbar hotbar;
    private final LobbyEntrance entrance;
    private final List<Location> landing;
    private final Location rally;
    private final Map<String, Cutscene> cutscenes;
    private final Cutscenes scenes;
    private final StoryNpcs npcs;
    private final Blasters blasters;
    private final ConsoleDefense defense;
    private final ChoiceRepository choices;
    private final SeenScenes seen;
    private final BossBar bar;
    private final Voices voices;
    private final DialogueHud dialogue;
    private final Set<UUID> party = new LinkedHashSet<>();
    private final Map<UUID, Choice> votes = new HashMap<>();
    private final String velaModel;
    private final NpcPresence vela;
    private final BukkitTask presence;
    private Quests quests;
    private Stage stage = Stage.BRIEFING;
    private Choice choice;
    private boolean briefed;

    public StoryGame(Plugin plugin, GameConfig gameConfig, Messages messages, World world, StatsRepository stats,
                     StatsMenus statsMenus, ChoiceRepository choices, SeenScenes seen) {
        this.plugin = plugin;
        this.messages = messages;
        this.world = world;
        this.choices = choices;
        var config = plugin.getConfig();
        var mission = config.getConfigurationSection("mission");
        this.landing = Spots.all(world, mission, "landing");
        this.rally = Spots.at(world, mission, "rally");
        this.cutscenes = loadCutscenes(world, config.getConfigurationSection("cutscenes.scenes"));
        this.summary = new RoundSummary(plugin, messages, new RoundSummary.Rewards(0, config.getInt("rewards.win")), stats);
        this.round = new Round(plugin, new Arena(messages, Sounds.JOIN), summary, config.getInt("players.max"), gameConfig);
        this.countdown = new LobbyCountdown(plugin, round, gameConfig, 1, ignored -> { }, this::start);
        this.hotbar = new LobbyHotbar(plugin, List.of(), statsMenus, countdown);
        this.entrance = new LobbyEntrance(round, countdown, hotbar, this::lobbySpawn, () -> Optional.of(rally),
                OptionalInt.empty(), this::greet);
        this.voices = Voices.load();
        warnLongVoices();
        this.seen = seen;
        var dialogueSettings = DialogueHud.Settings.from(config.getConfigurationSection("dialogue"),
                new DialogueHud.Settings.Hints(plain("dialogue.hint.advance"), plain("dialogue.hint.choose"), plain("dialogue.hint.close"),
                        speaker -> PlainTextComponentSerializer.plainText().serialize(
                                messages.get("dialogue.hint.watching", Placeholder.unparsed("player", speaker.getName())))));
        this.dialogue = new DialogueHud(plugin, dialogueSettings, round::players);
        this.scenes = new Cutscenes(plugin, messages, voices, seen, dialogueSettings.typing(), config.getInt("cutscenes.fade-ticks"));
        this.npcs = new StoryNpcs(plugin);
        this.blasters = new Blasters(plugin, config.getDouble("blaster.damage"), config.getInt("blaster.cooldown-ticks"),
                config.getDouble("blaster.range"));
        this.defense = new ConsoleDefense(plugin, ConsoleDefense.Settings.from(config.getConfigurationSection("defense")),
                Spots.all(world, mission, "droid-spawns"), round::players, blasters, messages.get("defense.droid"));
        this.bar = BossBar.bossBar(messages.get("stage.briefing"), 1, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        this.velaModel = mission.getString("vela-model", "");
        var post = Spots.at(world, mission, "vela");
        this.vela = new NpcPresence(spawnVela(post), post, round::players, dialogue);
        this.presence = Bukkit.getScheduler().runTaskTimer(plugin, vela::tick, 1, 1);
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
                name -> Cutscene.load(name, world, section.getConfigurationSection(name))));
    }

    private Mannequin spawnVela(Location at) {
        world.addPluginChunkTicket(at.getBlockX() >> 4, at.getBlockZ() >> 4, plugin);
        var vela = world.spawn(at, Mannequin.class, entity -> {
            entity.setPersistent(false);
            entity.setImmovable(true);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.customName(messages.get("npc.vela.name"));
            entity.setCustomNameVisible(true);
            entity.setDescription(messages.get("npc.vela.role"));
        });
        npcs.put("vela", vela);
        return vela;
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

    public void connect(Quests quests) {
        this.quests = quests;
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

    public Optional<Location> lobbySpawn() {
        return BlockcraftApi.get().waitingLobby().map(WaitingLobby::spawn).or(() -> Optional.of(rally));
    }

    private void greet(Player player) {
        seen.load(player);
        Tasks.async(plugin, () -> choices.find(player.getUniqueId(), MISSION), previous -> previous.ifPresent(earlier ->
                player.sendMessage(messages.get("lobby.previous", Placeholder.component("choice", messages.get("choice." + earlier.key()))))));
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
        equip(player);
        player.teleport(rally);
        player.showBossBar(bar);
        if (briefed) quests.enter(player, stage);
        player.sendMessage(messages.get("rejoin.stage", Placeholder.component("stage", messages.get("stage." + stage.key()))));
        choice().ifPresent(made -> player.sendMessage(messages.get("rejoin.choice", Placeholder.component("choice", messages.get("choice." + made.key())))));
    }

    @Override
    public void quit(Player player) {
        player.hideBossBar(bar);
        if (!round.stepOut(player, this::forfeited)) {
            round.leave(player);
        }
        tally();
    }

    private void forfeited(UUID member) {
        if (round.playerCount() == 0 && phase() == GamePhase.INGAME) round.end();
    }

    @Override
    public void vanished(Player player) {
        if (phase() == GamePhase.INGAME && round.isPlaying(player)) {
            round.leave(player);
            round.watch(player);
        }
    }

    @Override
    public void refreshAll() {
    }

    private void start() {
        round.enter(GamePhase.INGAME);
        // BetterModel loads its models after the plugins have enabled, so Vela gets her body when the mission starts.
        vela.dress(velaModel);
        var members = round.players();
        for (int i = 0; i < members.size(); i++) {
            var member = members.get(i);
            party.add(member.getUniqueId());
            equip(member);
            member.teleport(landing.get(i % landing.size()));
            member.showBossBar(bar);
        }
        play("intro", () -> {
            briefed = true;
            round.players().forEach(member -> {
                quests.enter(member, stage);
                StorySound.STAGE.play(member);
            });
            Bukkit.broadcast(messages.get("stage-start.briefing"));
        });
    }

    private void equip(Player player) {
        PlayerReset.reset(player, GameMode.ADVENTURE);
        player.getInventory().setItem(0, blasters.item(messages.get("item.blaster")));
    }

    /** {@code /storyscene <name>} plays a cutscene to the sender alone, to watch or trace it without a mission. */
    public LiteralCommandNode<CommandSourceStack> sceneCommand() {
        return Commands.literal("storyscene")
                .requires(source -> source.getSender() instanceof Player && source.getSender().hasPermission("blockcraft.story.scene"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            cutscenes.keySet().forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            var player = context.getSource().getPlayerOrThrow();
                            var cutscene = cutscenes.get(StringArgumentType.getString(context, "name"));
                            if (cutscene != null && !scenes.playing(player)) {
                                dialogue.close(player);
                                vela.hold(true);
                                scenes.play(cutscene, List.of(player), npcs.get("vela").map(LivingEntity.class::cast), () -> vela.hold(false));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private void play(String name, Runnable then) {
        var cutscene = cutscenes.get(name);
        if (cutscene == null) {
            then.run();
            return;
        }
        Optional<LivingEntity> actor = npcs.get("vela").map(LivingEntity.class::cast);
        round.players().forEach(dialogue::close);
        vela.hold(true);
        scenes.play(cutscene, round.players(), actor, () -> {
            vela.hold(false);
            then.run();
        });
    }

    @Override
    public Stage stage() {
        return stage;
    }

    @Override
    public Optional<Choice> choice() {
        return Optional.ofNullable(choice);
    }

    @Override
    public boolean voted(UUID player) {
        return votes.containsKey(player);
    }

    @Override
    public void advance(Stage next) {
        if (phase() != GamePhase.INGAME || next.ordinal() <= stage.ordinal() || next.ordinal() >= Stage.ENDING.ordinal()) return;
        stage = next;
        bar.name(messages.get("stage." + stage.key()));
        bar.progress(stage == Stage.DEFENSE ? 0 : 1);
        bar.color(stage == Stage.DEFENSE ? BossBar.Color.RED : BossBar.Color.BLUE);
        Bukkit.broadcast(messages.get("stage-start." + stage.key()));
        round.players().forEach(member -> {
            quests.enter(member, stage);
            (stage == Stage.REPORT ? StorySound.OBJECTIVE_COMPLETE : StorySound.STAGE).play(member);
        });
    }

    @Override
    public void consoleReached(ConsoleObjective console) {
        if (stage != Stage.DEFENSE || defense.running()) return;
        Bukkit.broadcast(messages.get("defense.started"));
        round.players().forEach(StorySound.OBJECTIVE_START::play);
        defense.start(console, progress -> bar.progress((float) progress), () -> Bukkit.broadcast(messages.get("defense.held")));
    }

    @Override
    public void vote(Player voter, Choice wanted) {
        if (stage != Stage.REPORT || choice != null || !round.isPlaying(voter)) return;
        votes.put(voter.getUniqueId(), wanted);
        Bukkit.broadcast(messages.get("vote.cast", Placeholder.unparsed("player", voter.getName()),
                Placeholder.component("choice", messages.get("choice." + wanted.key())),
                Formatter.number("votes", votes.size()), Formatter.number("members", round.playerCount())));
        round.players().forEach(StorySound.VOTE_CAST::play);
        tally();
    }

    /**
     * Decides once every member who is here has voted: the majority wins, and a tie goes to the party leader, the
     * member who joined first. A member who is away does not hold up the others.
     */
    private void tally() {
        if (stage != Stage.REPORT || choice != null || votes.isEmpty()) return;
        var here = round.players();
        if (!here.stream().allMatch(member -> votes.containsKey(member.getUniqueId()))) return;
        var counts = new EnumMap<Choice, Integer>(Choice.class);
        here.forEach(member -> counts.merge(votes.get(member.getUniqueId()), 1, Integer::sum));
        int most = counts.values().stream().max(Integer::compare).orElse(0);
        var leading = counts.entrySet().stream().filter(entry -> entry.getValue() == most).map(Map.Entry::getKey).toList();
        var leader = party.stream().filter(votes::containsKey).findFirst().map(votes::get);
        decide(leading.size() == 1 ? leading.getFirst() : leader.filter(leading::contains).orElse(leading.getFirst()));
    }

    private void decide(Choice decided) {
        choice = decided;
        stage = Stage.ENDING;
        var members = List.copyOf(party);
        Tasks.async(plugin, () -> members.forEach(member -> choices.record(member, MISSION, decided)));
        Bukkit.broadcast(messages.get("vote.decided", Placeholder.component("choice", messages.get("choice." + decided.key()))));
        bar.name(messages.get("stage.ending"));
        round.players().forEach(StorySound.VOTE_DECIDED::play);
        Tasks.later(plugin, 30, () -> play("ending-" + decided.key(), this::finish));
    }

    private void finish() {
        stage = Stage.DONE;
        var title = Title.title(messages.get("ending." + choice.key() + ".title"), messages.get("ending." + choice.key() + ".subtitle"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(800)));
        var sting = choice == Choice.FABRIK ? StorySound.ENDING_FABRIK : StorySound.ENDING_GEFANGENE;
        for (var member : round.players()) {
            member.hideBossBar(bar);
            member.showTitle(title);
            sting.play(member);
            summary.awardWin(member);
        }
        Bukkit.broadcast(messages.get("ending." + choice.key() + ".summary"));
        Tasks.later(plugin, ENDING_TITLE_TICKS, round::end);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
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
        npcs.get("vela").ifPresent(Mannequin::remove);
        round.stop();
    }
}
