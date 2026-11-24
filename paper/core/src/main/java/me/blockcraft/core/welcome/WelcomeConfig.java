package me.blockcraft.core.welcome;

import me.blockcraft.common.config.StrictConfig;

import java.util.List;

/** The {@code welcome} section of config.yml. */
public record WelcomeConfig(List<String> words, int reward, int dailyCap, int committeeThreshold) {

    public static WelcomeConfig from(StrictConfig welcome) {
        return new WelcomeConfig(welcome.texts("words"), welcome.integer("reward-coins"), welcome.integer("daily-cap"),
                welcome.integer("committee-threshold"));
    }
}
