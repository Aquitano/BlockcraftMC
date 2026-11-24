package me.blockcraft.api.cosmetic;

import me.blockcraft.common.message.Messages;
import me.blockcraft.common.settings.PlayerSetting;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.Optional;

public sealed interface CosmeticReward {

    String item();

    PlayerSetting setting();

    String value();

    Material icon();

    Component name(Messages messages);

    static Optional<CosmeticReward> parse(String item) {
        if (item.startsWith(ChatTagChoice.ITEM_PREFIX)) {
            return ChatTagChoice.parse(item.substring(ChatTagChoice.ITEM_PREFIX.length()))
                    .filter(choice -> choice.tag().earned()).map(Tag::new);
        }
        if (item.startsWith("effect.kill.")) {
            return KillEffect.byKey(item.substring("effect.kill.".length())).filter(KillEffect::earned).map(Kill::new);
        }
        if (item.startsWith("effect.win.")) {
            return WinEffect.byKey(item.substring("effect.win.".length())).filter(WinEffect::earned).map(Win::new);
        }
        return Optional.empty();
    }

    record Tag(ChatTagChoice choice) implements CosmeticReward {

        public Tag {
            if (!choice.tag().earned()) {
                throw new IllegalArgumentException("A cosmetic reward must be earned");
            }
        }

        @Override
        public String item() {
            return choice.item();
        }

        @Override
        public PlayerSetting setting() {
            return PlayerSetting.CHAT_TAG;
        }

        @Override
        public String value() {
            return choice.value();
        }

        @Override
        public Material icon() {
            return Material.NAME_TAG;
        }

        @Override
        public Component name(Messages messages) {
            return choice.text(messages);
        }
    }

    record Kill(KillEffect effect) implements CosmeticReward {

        public Kill {
            if (!effect.earned()) {
                throw new IllegalArgumentException("A cosmetic reward must be earned");
            }
        }

        @Override
        public String item() {
            return effect.item();
        }

        @Override
        public PlayerSetting setting() {
            return PlayerSetting.KILL_EFFECT;
        }

        @Override
        public String value() {
            return effect.key();
        }

        @Override
        public Material icon() {
            return effect.icon();
        }

        @Override
        public Component name(Messages messages) {
            return messages.get("level.rewards." + effect.key());
        }
    }

    record Win(WinEffect effect) implements CosmeticReward {

        public Win {
            if (!effect.earned()) {
                throw new IllegalArgumentException("A cosmetic reward must be earned");
            }
        }

        @Override
        public String item() {
            return effect.item();
        }

        @Override
        public PlayerSetting setting() {
            return PlayerSetting.WIN_EFFECT;
        }

        @Override
        public String value() {
            return effect.key();
        }

        @Override
        public Material icon() {
            return effect.icon();
        }

        @Override
        public Component name(Messages messages) {
            return messages.get("level.rewards." + effect.key());
        }
    }
}
