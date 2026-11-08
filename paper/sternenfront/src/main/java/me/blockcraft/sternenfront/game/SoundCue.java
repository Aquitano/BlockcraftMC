package me.blockcraft.sternenfront.game;

import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.function.BiFunction;
import java.util.stream.Stream;

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
    SABER_WHIRL("saber.whirl"),
    SABER_GUARD("saber.guard"),
    FORCE_PUSH("force.push"),
    FORCE_PUSH_CHARGE("force.push_charge"),
    FORCE_GRIP("force.grip"),
    FORCE_LIGHTNING("force.lightning"),
    WRIST_ROCKET_LAUNCH("wrist_rocket.launch"),
    WRIST_ROCKET_IMPACT("wrist_rocket.impact"),
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
    COOLED("cooled"),
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

    public void play(Location at, float volume) {
        play(at, volume, 1f);
    }

    /** Heard by everyone within 16 blocks per unit of volume. */
    public void play(Location at, float volume, float shift) {
        listenersInReach(at, volume).forEach(listener -> listener.playSound(at, key, SoundCategory.PLAYERS, volume, shift));
    }

    /** As {@link #play(Location, float, float)}, but the sound rides on {@code source}, which every client draws as itself. */
    public void playOn(Entity source, float volume, float shift) {
        playOn(source, volume, shift, (listener, entity) -> entity);
    }

    /**
     * As {@link #play(Location, float, float)}, but each listener hears it on what their client draws of
     * {@code source}, so it follows as that moves. A client drops a sound on an entity it does not draw.
     */
    public void playOn(Entity source, float volume, float shift, BiFunction<Player, Entity, Entity> drawnAs) {
        listenersInReach(source.getLocation(), volume)
                .forEach(listener -> listener.playSound(drawnAs.apply(listener, source), key, SoundCategory.PLAYERS, volume, shift));
    }

    /** As {@link #play(Location, float, float)} from {@code source}'s eyes, except that a player hears their own at full volume. */
    public void playFrom(LivingEntity source, float volume, float shift) {
        var at = source.getEyeLocation();
        if (source instanceof Player player) {
            playTo(player, 1f, shift);
        }
        listenersInReach(at, volume).filter(listener -> !listener.equals(source))
                .forEach(listener -> listener.playSound(at, key, SoundCategory.PLAYERS, volume, shift));
    }

    public void playTo(Player listener, float volume) {
        playTo(listener, volume, 1f);
    }

    /** Only {@code listener} hears it, from where they stand, and it follows them as they move. */
    public void playTo(Player listener, float volume, float shift) {
        listener.playSound(listener, key, SoundCategory.PLAYERS, volume, shift);
    }

    private static Stream<Player> listenersInReach(Location at, float volume) {
        double reach = BLOCKS_PER_VOLUME * Math.max(1, volume);
        return at.getWorld().getPlayers().stream().filter(listener -> listener.getLocation().distanceSquared(at) <= reach * reach);
    }
}
