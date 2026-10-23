package me.blockcraft.common.sound;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import java.util.Optional;

public enum FeedbackSound {

    MENU_CLICK("ui.button.click", 0.6f, 1.2f, "click"),
    MENU_DENY("entity.villager.no", 0.8f, 1.0f),
    MENU_OPEN("block.chest.open", 0.6f, 1.2f, "open"),
    TELEPORT("entity.enderman.teleport", 0.8f, 1.0f),
    COUNTDOWN_TICK("block.note_block.pling", 1.0f, 1.0f),
    COUNTDOWN_GO("block.note_block.pling", 1.0f, 2.0f),
    COINS("entity.experience_orb.pickup", 0.7f, 1.4f),
    PURCHASE("entity.player.levelup", 0.8f, 1.4f, "purchase"),
    KILL("entity.player.attack_crit", 1.0f, 1.0f),
    STREAK("item.trident.thunder", 0.6f, 1.3f),
    WIN("ui.toast.challenge_complete", 1.0f, 1.0f, "win"),
    LOSE("block.note_block.bass", 0.5f, 0.8f),
    LEVEL_UP("entity.player.levelup", 1.0f, 1.0f),
    ACHIEVEMENT("ui.toast.challenge_complete", 0.8f, 1.3f),
    JOIN("block.note_block.bell", 0.6f, 1.6f),
    QUICKSG_JOIN("item.crossbow.loading_end", 0.5f, 1.1f),
    RUSH_JOIN("block.amethyst_block.chime", 0.5f, 1.2f),
    DUELL_JOIN("block.anvil.place", 0.25f, 1.5f),
    NOTIFY("block.note_block.pling", 0.8f, 1.8f, "notify"),
    SECRET("entity.ender_dragon.growl", 0.6f, 1.2f),
    DOUBLE_JUMP("entity.ender_dragon.flap", 1.0f, 2.0f),
    JUMP_PAD("entity.arrow.hit", 1.0f, 0.5f),
    GRAPPLE("entity.fishing_bobber.retrieve", 1.0f, 0.8f),
    PAINT_THROW("entity.snowball.throw", 1.0f, 1.0f),
    EQUIP("item.armor.equip_leather", 0.5f, 1.2f, "equip"),
    HEAL("entity.experience_orb.pickup", 0.5f, 1.0f),
    PVP_ENABLED("block.note_block.bell", 0.7f, 0.8f),
    NOTE_BIT("block.note_block.bit", 0.8f, 1.0f),
    NOTE_PLING("block.note_block.pling", 0.7f, 1.0f),
    NOTE_BASS("block.note_block.bass", 0.8f, 1.0f),
    SHOP_TALK("entity.villager.ambient", 0.8f, 1.0f),
    SHOP_HURT("entity.villager.hurt", 0.8f, 1.0f),
    PLAYER_HURT("entity.player.hurt", 1.0f, 1.0f),
    EASTER_EGG("entity.chicken.egg", 1.0f, 1.3f),
    ADVENT_DOOR("block.note_block.chime", 0.9f, 1.2f),
    TRICK("entity.witch.celebrate", 0.8f, 1.0f);

    private final Sound fallback;
    private final Optional<Sound> custom;

    FeedbackSound(String key, float volume, float pitch) {
        this(key, volume, pitch, null);
    }

    FeedbackSound(String key, float volume, float pitch, String customKey) {
        fallback = Sound.sound(Key.key(key), Sound.Source.MASTER, volume, pitch);
        custom = Optional.ofNullable(customKey).map(name ->
                Sound.sound(Key.key("blockcraft", "ui." + name), Sound.Source.MASTER, volume, 1.0f));
    }

    public Sound sound() {
        return fallback;
    }

    public Sound sound(boolean packApplied) {
        return packApplied ? custom.orElse(fallback) : fallback;
    }
}
