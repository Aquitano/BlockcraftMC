package me.blockcraft.lobby.menu;

import me.blockcraft.api.cosmetic.KillEffect;
import me.blockcraft.api.cosmetic.WinEffect;
import me.blockcraft.common.settings.PlayerSetting;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.BiConsumer;

/** The two effect enums share no type, so the menu reads them through this description. */
record EffectKind<E extends Enum<E>>(
        PlayerSetting setting,
        String messagePrefix,
        String itemPrefix,
        List<E> values,
        Function<E, String> key,
        Function<E, Material> icon,
        Function<E, String> item,
        BiConsumer<Player, E> preview
) {

    static final EffectKind<KillEffect> KILL = new EffectKind<>(PlayerSetting.KILL_EFFECT, "effects.kill", "effect.kill.",
            Arrays.asList(KillEffect.values()), KillEffect::key, KillEffect::icon, KillEffect::item, EffectPreviews::kill);
    static final EffectKind<WinEffect> WIN = new EffectKind<>(PlayerSetting.WIN_EFFECT, "effects.win", "effect.win.",
            Arrays.asList(WinEffect.values()), WinEffect::key, WinEffect::icon, WinEffect::item, EffectPreviews::win);
}
