package me.blockcraft.sternenfront.game;

import me.blockcraft.api.config.BundledDefaults;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;

/** The {@code intro} section: the camera flight before a round, its radio briefing and its {@code shots}. */
record IntroConfig(boolean enabled, int durationTicks, int fadeTicks, BattleIntro.FocusWindow focus, Audio audio,
                   List<Map<?, ?>> shots) {

    record Audio(boolean enabled, boolean briefing, float voiceVolume, float effectsVolume, boolean captions, int briefingTicks) { }

    static IntroConfig from(ConfigurationSection section) {
        var intro = BundledDefaults.strict(section).section("intro");
        var audio = intro.section("audio");
        return new IntroConfig(intro.flag("enabled"), Math.clamp(intro.integer("duration-ticks"), 40, 600),
                intro.integer("fade-ticks"),
                new BattleIntro.FocusWindow(intro.flag("focus.enabled"), intro.decimal("focus.from"), intro.decimal("focus.until")),
                new Audio(audio.flag("enabled"), audio.flag("briefing"), (float) Math.clamp(audio.decimal("voice-volume"), 0, 1),
                        (float) Math.clamp(audio.decimal("effects-volume"), 0, 1), audio.flag("captions"),
                        Math.clamp(audio.integer("briefing-ticks"), 48, 200)),
                List.copyOf(section.getMapList("intro.shots")));
    }
}
