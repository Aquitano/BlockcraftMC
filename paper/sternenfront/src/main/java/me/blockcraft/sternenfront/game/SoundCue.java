package me.blockcraft.sternenfront.game;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/** Sternenfront's sounds, the round's and its secrets', each the pack's {@code blockcraft:sternenfront.<key>}. */
public enum SoundCue {
    BLASTER_CARBINE("blaster.carbine"),
    BLASTER_REPEATER("blaster.repeater"),
    BLASTER_LONG_RIFLE("blaster.long_rifle"),
    BLASTER_PISTOL("blaster.pistol"),
    BLASTER_TWIN_PISTOL("blaster.twin_pistol"),
    BLASTER_HEAVY_RIFLE("blaster.heavy_rifle"),
    BLASTER_SPEEDER_CANNON("blaster.heavy_rifle"),
    BOLT_IMPACT("bolt.impact"),
    SABER_IGNITE("saber.ignite"),
    SABER_RETRACT("saber.retract"),
    SABER_HUM("saber.hum"),
    SABER_SWING("saber.swing"),
    SABER_HIT("saber.hit"),
    SABER_CLASH("saber.clash"),
    SABER_DEFLECT("saber.deflect"),
    SABER_THROW("saber.throw"),
    FORCE_PUSH("force.push"),
    FORCE_GRIP("force.grip"),
    FORCE_LIGHTNING("bolt.impact"),
    WRIST_ROCKET_LAUNCH("jetpack"),
    WRIST_ROCKET_IMPACT("grenade.explode"),
    GRENADE_BEEP("grenade.beep"),
    GRENADE_EXPLODE("grenade.explode"),
    CHARGE_ARM("charge.arm"),
    SIEGE_LOCK("siege.lock"),
    SHIELD_UP("shield.up"),
    SHIELD_DOWN("shield.down"),
    JETPACK("jetpack"),
    GRAPPLE("grapple"),
    RALLY("rally"),
    ORBITAL_INCOMING("orbital.incoming"),
    ORBITAL_IMPACT("orbital.impact"),
    HITMARKER("hitmarker"),
    HEADSHOT("headshot"),
    KYBER_FOUND("kyber.found"),
    BRUMMI_BEEP("brummi.beep"),
    BRUMMI_SCARED("brummi.scared"),
    SAND_BEAST_RUMBLE("sand_beast.rumble"),
    SAND_BEAST_ROAR("sand_beast.roar"),
    KILL("kill");

    private static final double BLOCKS_PER_VOLUME = 16;

    private final String key;

    SoundCue(String key) {
        this.key = "blockcraft:sternenfront." + key;
    }

    public static SoundCue shot(Blaster blaster) {
        return valueOf("BLASTER_" + blaster.profile().name());
    }

    public void play(Location at, float volume) {
        play(at, volume, 1f);
    }

    /** Heard by everyone within 16 blocks per unit of volume. */
    public void play(Location at, float volume, float shift) {
        double reach = BLOCKS_PER_VOLUME * Math.max(1, volume);
        for (var listener : at.getWorld().getPlayers()) {
            if (listener.getLocation().distanceSquared(at) <= reach * reach) {
                listener.playSound(at, key, SoundCategory.PLAYERS, volume, shift);
            }
        }
    }

    /** Only {@code listener} hears it, as if it came from where they stand. */
    public void playTo(Player listener, float volume) {
        listener.playSound(listener.getLocation(), key, SoundCategory.PLAYERS, volume, 1f);
    }
}
