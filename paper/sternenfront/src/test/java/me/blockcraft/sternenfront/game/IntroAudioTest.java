package me.blockcraft.sternenfront.game;

import me.blockcraft.api.presentation.PresentationScope;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.Test;

import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class IntroAudioTest {
    @Test
    public void factionRadioFollowsTheCameraAndStopsWithItsScene() throws Exception {
        var config = config();
        var route = route(config, 180);
        for (var faction : Faction.values()) {
            var played = new ArrayList<Played>();
            var stopped = new ArrayList<String>();
            var camera = fake(Entity.class, (proxy, method, args) -> {
                throw new AssertionError(method.getName());
            });
            var player = fake(Player.class, (proxy, method, args) -> switch (method.getName()) {
                case "getSpectatorTarget" -> camera;
                case "playSound" -> {
                    played.add(new Played((Entity) args[0], (String) args[1], (SoundCategory) args[2]));
                    yield null;
                }
                case "stopSound" -> {
                    stopped.add(args[0] + "/" + args[1]);
                    yield null;
                }
                default -> throw new AssertionError(method.getName());
            });
            var scope = scope();
            var captions = new AtomicInteger();
            var cues = new IntroAudio(player, scope).cues(faction, route, config, captions::incrementAndGet);
            assertEquals(List.of(15, 18, 72, 98, 135), cues.stream().map(cue -> cue.tick()).toList());
            cues.forEach(cue -> cue.action().run());
            assertEquals(1, captions.get());
            assertEquals(5, played.size());
            assertTrue(played.stream().allMatch(sound -> sound.emitter() == camera));
            var voice = played.stream().filter(sound -> sound.category() == SoundCategory.VOICE).toList();
            assertEquals(1, voice.size());
            assertEquals("blockcraft:sternenfront.intro.briefing." + faction.key(), voice.getFirst().key());
            scope.close();
            scope.close();
            assertEquals(played.stream().map(sound -> sound.key() + "/" + sound.category()).sorted().toList(),
                    stopped.stream().sorted().toList());
            cues.forEach(cue -> cue.action().run());
            assertEquals(5, played.size());
            assertEquals(1, captions.get());
        }
    }

    @Test
    public void shortIntrosOmitTheVoiceInsteadOfCuttingItOffAndDisabledAudioHasNoCues() throws Exception {
        var config = config();
        var player = fake(Player.class, (proxy, method, args) -> {
            throw new AssertionError(method.getName());
        });
        var audio = new IntroAudio(player, scope());
        assertEquals(List.of(16), audio.cues(Faction.KOLONIEN, route(config, 40), config, () -> fail()).stream()
                .map(cue -> cue.tick()).toList());
        for (int duration = 40; duration <= 600; duration++) {
            var cues = audio.cues(Faction.HEGEMONIE, route(config, duration), config, () -> fail());
            for (var cue : cues) assertTrue(cue.tick() >= 0 && cue.tick() < duration);
        }
        config.set("intro.audio.enabled", false);
        assertTrue(audio.cues(Faction.KOLONIEN, route(config, 180), config, () -> fail()).isEmpty());
    }

    private static BattleIntro.IntroPath route(YamlConfiguration config, int duration) {
        return BattleIntro.path(config.getMapList("intro.shots"), duration, ignored -> new Location(null, 0, 0, 0));
    }

    private static YamlConfiguration config() throws Exception {
        try (var input = new InputStreamReader(IntroAudioTest.class.getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(input);
        }
    }

    private static PresentationScope scope() {
        return new PresentationScope(fake(Plugin.class, (proxy, method, args) -> {
            throw new AssertionError(method.getName());
        }));
    }

    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private record Played(Entity emitter, String key, SoundCategory category) { }
}
