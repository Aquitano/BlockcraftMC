package me.blockcraft.sternenfront.game;

import me.blockcraft.api.BlockcraftApi;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * Sternenfront's sounds, the round's and its secrets'. A listener who wears the pack hears its own sound {@code blockcraft:sternenfront.<key>}, everyone
 * else the vanilla fallback, so a client that declined the pack never hears silence. The kill sound has none: the
 * round summary already plays the network's kill sound to everyone.
 */
public enum SoundCue {
    BLASTER_CARBINE("blaster.carbine", Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.9f),
    BLASTER_REPEATER("blaster.repeater", Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.4f),
    BLASTER_LONG_RIFLE("blaster.long_rifle", Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.8f),
    BLASTER_PISTOL("blaster.pistol", Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f),
    BLASTER_TWIN_PISTOL("blaster.twin_pistol", Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.7f),
    BLASTER_HEAVY_RIFLE("blaster.heavy_rifle", Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.1f),
    BOLT_IMPACT("bolt.impact", Sound.BLOCK_AMETHYST_CLUSTER_HIT, 1.6f),
    SABER_IGNITE("saber.ignite", Sound.BLOCK_BEACON_ACTIVATE, 2.0f),
    SABER_RETRACT("saber.retract", Sound.BLOCK_BEACON_DEACTIVATE, 2.0f),
    SABER_HUM("saber.hum", Sound.BLOCK_BEACON_AMBIENT, 1.5f),
    SABER_SWING("saber.swing", Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f),
    SABER_HIT("saber.hit", Sound.BLOCK_FIRE_EXTINGUISH, 1.3f),
    SABER_CLASH("saber.clash", Sound.BLOCK_ANVIL_LAND, 2.0f),
    SABER_DEFLECT("saber.deflect", Sound.ITEM_SHIELD_BLOCK, 1.5f),
    SABER_THROW("saber.throw", Sound.ITEM_TRIDENT_THROW, 1.2f),
    FORCE_PUSH("force.push", Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.8f),
    FORCE_GRIP("force.grip", Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f),
    GRENADE_BEEP("grenade.beep", Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f),
    GRENADE_EXPLODE("grenade.explode", Sound.ENTITY_GENERIC_EXPLODE, 1.3f),
    SHIELD_UP("shield.up", Sound.BLOCK_BEACON_ACTIVATE, 1.6f),
    SHIELD_DOWN("shield.down", Sound.BLOCK_BEACON_DEACTIVATE, 1.6f),
    JETPACK("jetpack", Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.7f),
    GRAPPLE("grapple", Sound.ENTITY_FISHING_BOBBER_THROW, 0.8f),
    RALLY("rally", Sound.ITEM_GOAT_HORN_SOUND_0, 1.0f),
    ORBITAL_INCOMING("orbital.incoming", Sound.BLOCK_BEACON_POWER_SELECT, 0.5f),
    ORBITAL_IMPACT("orbital.impact", Sound.ENTITY_GENERIC_EXPLODE, 0.8f),
    HITMARKER("hitmarker", Sound.ENTITY_ARROW_HIT_PLAYER, 1.6f),
    HEADSHOT("headshot", Sound.BLOCK_NOTE_BLOCK_BELL, 2.0f),
    KYBER_FOUND("kyber.found", Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f),
    BRUMMI_BEEP("brummi.beep", Sound.BLOCK_NOTE_BLOCK_BIT, 1.7f),
    BRUMMI_SCARED("brummi.scared", Sound.ENTITY_FOX_SCREECH, 1.6f),
    SAND_BEAST_RUMBLE("sand_beast.rumble", Sound.ENTITY_WARDEN_DIG, 0.6f),
    SAND_BEAST_ROAR("sand_beast.roar", Sound.ENTITY_RAVAGER_ROAR, 0.5f),
    KILL("kill", null, 1f);

    private static final double BLOCKS_PER_VOLUME = 16;

    private final String key;
    private final Sound fallback;
    private final float fallbackPitch;

    SoundCue(String key, Sound fallback, float fallbackPitch) {
        this.key = "blockcraft:sternenfront." + key;
        this.fallback = fallback;
        this.fallbackPitch = fallbackPitch;
    }

    public static SoundCue shot(Blaster blaster) {
        return valueOf("BLASTER_" + blaster.name());
    }

    public void play(Location at, float volume) {
        play(at, volume, 1f);
    }

    /** Heard by everyone within 16 blocks per unit of volume. */
    public void play(Location at, float volume, float shift) {
        double reach = BLOCKS_PER_VOLUME * Math.max(1, volume);
        for (var listener : at.getWorld().getPlayers()) {
            if (listener.getLocation().distanceSquared(at) <= reach * reach) {
                play(listener, at, volume, shift);
            }
        }
    }

    /** Only {@code listener} hears it, as if it came from where they stand. */
    public void playTo(Player listener, float volume) {
        play(listener, listener.getLocation(), volume, 1f);
    }

    private void play(Player listener, Location at, float volume, float shift) {
        if (BlockcraftApi.get().resourcePack().applied(listener)) {
            listener.playSound(at, key, SoundCategory.PLAYERS, volume, shift);
        } else if (fallback != null) {
            listener.playSound(at, fallback, SoundCategory.PLAYERS, volume, fallbackPitch * shift);
        }
    }
}
