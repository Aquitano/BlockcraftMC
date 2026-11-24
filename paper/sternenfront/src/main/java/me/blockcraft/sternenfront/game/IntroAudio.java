package me.blockcraft.sternenfront.game;

import me.blockcraft.api.presentation.PresentationScope;
import me.blockcraft.api.scene.SceneTimeline;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class IntroAudio {
    private enum Sound {
        RADIO_OPEN("radio_open", SoundCategory.AMBIENT),
        RADIO_CLOSE("radio_close", SoundCategory.AMBIENT),
        TRANSITION("transition", SoundCategory.AMBIENT),
        KOLONIEN("briefing.kolonien", SoundCategory.VOICE),
        HEGEMONIE("briefing.hegemonie", SoundCategory.VOICE);

        final String key;
        final SoundCategory category;

        Sound(String key, SoundCategory category) {
            this.key = "blockcraft:sternenfront.intro." + key;
            this.category = category;
        }
    }

    private final Player player;
    private final PresentationScope scope;

    IntroAudio(Player player, PresentationScope scope) {
        this.player = player;
        this.scope = scope;
    }

    List<SceneTimeline.Cue> cues(Faction faction, BattleIntro.IntroPath route, IntroConfig config, Runnable caption) {
        var audio = config.audio();
        if (!audio.enabled()) return List.of();
        int duration = route.camera().duration();
        int fade = Math.clamp(config.fadeTicks(), 1, duration / 2);
        float voiceVolume = audio.voiceVolume();
        float effectsVolume = audio.effectsVolume();
        int speechStart = fade + 3;
        int speechEnd = speechStart + audio.briefingTicks();
        var cues = new ArrayList<SceneTimeline.Cue>();
        if (audio.briefing() && speechEnd + 4 <= duration - fade) {
            cues.add(cue(fade, Sound.RADIO_OPEN, effectsVolume));
            cues.add(new SceneTimeline.Cue(speechStart, () -> {
                if (scope.closed()) return;
                play(faction == Faction.KOLONIEN ? Sound.KOLONIEN : Sound.HEGEMONIE, voiceVolume);
                if (audio.captions()) caption.run();
            }));
            cues.add(cue(speechEnd, Sound.RADIO_CLOSE, effectsVolume));
        }
        int postStart = route.camera().frames().get(1).tick();
        if (postStart >= fade && postStart < duration - fade) {
            cues.add(cue(postStart, Sound.TRANSITION, effectsVolume));
        }
        if (route.briefingStart() != postStart && route.briefingStart() >= fade && route.briefingStart() < duration - fade) {
            cues.add(cue(route.briefingStart(), Sound.TRANSITION, effectsVolume));
        }
        cues.sort(Comparator.comparingInt(SceneTimeline.Cue::tick));
        return List.copyOf(cues);
    }

    private SceneTimeline.Cue cue(int tick, Sound sound, float volume) {
        return new SceneTimeline.Cue(tick, () -> play(sound, volume));
    }

    private void play(Sound sound, float volume) {
        if (scope.closed() || volume == 0) return;
        var camera = player.getSpectatorTarget();
        player.playSound(camera == null ? player : camera, sound.key, sound.category, volume, 1);
        scope.own(() -> player.stopSound(sound.key, sound.category));
    }
}
