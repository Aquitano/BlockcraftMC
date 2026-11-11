package me.blockcraft.story.debug;

import com.google.gson.GsonBuilder;
import me.blockcraft.story.cutscene.Cutscenes;
import me.blockcraft.story.dialogue.DialogueHud;
import me.blockcraft.story.game.Stage;
import me.blockcraft.story.game.StoryGame;
import me.blockcraft.story.sound.Soundtrack;
import me.blockcraft.story.state.Timers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Everything about the party's story at one moment, for {@code /story state}, its JSON and the debug sidebar. */
public record StoryState(String phase, String stage, int stageNumber, List<String> stages, List<String> members,
                         Map<String, String> facts, Optional<Cutscenes.Progress> scene, Optional<Double> upload,
                         List<Timer> timers, Optional<String> music, Optional<String> ambience,
                         Map<String, Map<String, String>> votes, Map<String, DialogueHud.Panel> panels,
                         Optional<String> campaign) {

    public record Timer(String label, long inTicks) { }

    public static StoryState of(StoryGame game) {
        long now = Bukkit.getCurrentTick();
        var stages = game.stages().stream().map(Stage::name).toList();
        var panels = new LinkedHashMap<String, DialogueHud.Panel>();
        game.members().forEach(member -> game.dialogue().panel(member).ifPresent(panel -> panels.put(member.getName(), panel)));
        return new StoryState(game.phase().name(), game.stage(), stages.indexOf(game.stage()) + 1, stages,
                game.members().stream().map(Player::getName).toList(), game.facts().ordered(), game.sceneProgress(), game.upload(),
                game.timers().pending().stream().map((Timers.Pending pending) -> new Timer(pending.label(), pending.due() - now)).toList(),
                game.music().playing(Soundtrack.Channel.MUSIC), game.music().playing(Soundtrack.Channel.AMBIENCE), game.openVotes(), panels,
                game.campaign());
    }

    public List<String> lines() {
        var lines = new ArrayList<String>();
        lines.add("stage " + (stage.isEmpty() ? "-" : stage + " (" + stageNumber + "/" + stages.size() + ")") + ", round " + phase
                + ", members " + String.join(", ", members));
        lines.add("facts " + (facts.isEmpty() ? "-" : facts.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).toList()));
        scene.ifPresent(at -> lines.add("scene " + at.scene() + " " + at.tick() + "/" + at.duration() + " for " + at.viewers()));
        upload.ifPresent(done -> lines.add("upload " + Math.round(done * 100) + "%"));
        timers.forEach(timer -> lines.add("timer in " + timer.inTicks() + " ticks: " + timer.label()));
        lines.add("music " + music.orElse("-") + ", ambience " + ambience.orElse("-"));
        votes.forEach((topic, cast) -> lines.add("vote " + topic + " " + cast));
        campaign.ifPresent(saved -> lines.add("campaign " + saved));
        panels.forEach((member, panel) -> lines.add("panel " + member + ": " + panel.speaker() + " page " + panel.page() + "/"
                + panel.pages() + (panel.choosing() ? ", choosing " + panel.selected() + " of " + panel.answers() : "")));
        return lines;
    }

    public String json() {
        var json = new LinkedHashMap<String, Object>(Map.of(
                "phase", phase, "stage", stage, "stages", stages, "members", members, "facts", facts,
                "scene", scene.map(at -> Map.of("name", at.scene(), "tick", at.tick(), "duration", at.duration())).orElse(Map.of()),
                "upload", upload.orElse(-1.0),
                "timers", timers,
                "soundtrack", Map.of("music", music.orElse(""), "ambience", ambience.orElse("")),
                "votes", votes));
        json.put("panels", panels);
        json.put("campaign", campaign.orElse(""));
        return new GsonBuilder().create().toJson(json);
    }
}
