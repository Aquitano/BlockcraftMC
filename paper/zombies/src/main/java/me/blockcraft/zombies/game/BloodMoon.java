package me.blockcraft.zombies.game;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.zombies.config.ZombiesConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** A whole wave under a red night: the horde runs faster and every kill pays more. */
final class BloodMoon implements WaveEvent {

    private static final NamespacedKey SPEED = new NamespacedKey("blockcraft", "zombies_blood_moon");
    private static final long MIDNIGHT = 18000;
    private static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(0xB0, 0x10, 0x10), 1.6f);
    private final ZombiesGame game;
    private final ArenaEffects effects;
    private final ZombiesConfig.BloodMoon config;

    BloodMoon(ZombiesGame game, ArenaEffects effects, ZombiesConfig.BloodMoon config) {
        this.game = game;
        this.effects = effects;
        this.config = config;
    }

    @Override
    public Kind kind() {
        return Kind.BLOOD_MOON;
    }

    @Override
    public void start() {
        game.announce("event.blood_moon", Sounds.SCARE, Formatter.number("wave", game.wave()),
                Formatter.number("multiplier", config.goldMultiplier()));
        effects.bloodHorn();
        night();
    }

    @Override
    public boolean tick(int tick) {
        if (tick % 20 == 0) {
            night();
        }
        if (tick % 5 == 0) {
            for (var mob : game.enemies().living()) {
                hasten(mob);
                mob.getWorld().spawnParticle(Particle.DUST, mob.getLocation().add(0, 1, 0), 2, 0.25, 0.5, 0.25, 0, RED);
            }
            var random = ThreadLocalRandom.current();
            for (var fighter : game.fighters()) {
                var around = fighter.getLocation().add(random.nextDouble(-6, 6), random.nextDouble(1, 4), random.nextDouble(-6, 6));
                fighter.getWorld().spawnParticle(Particle.DUST, around, 6, 1.5, 0.8, 1.5, 0, RED);
            }
        }
        return true;
    }

    int killGold(int gold) {
        return gold * config.goldMultiplier();
    }

    @Override
    public Component goal() {
        return game.messages().get("event.blood_moon.goal", Formatter.number("multiplier", config.goldMultiplier()));
    }

    @Override
    public void end() {
        Bukkit.getOnlinePlayers().forEach(Player::resetPlayerTime);
        game.enemies().living().forEach(mob -> {
            var speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                speed.removeModifier(SPEED);
            }
        });
    }

    private void night() {
        Bukkit.getOnlinePlayers().forEach(player -> player.setPlayerTime(MIDNIGHT, false));
    }

    private void hasten(Mob mob) {
        var speed = mob.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SPEED) == null) {
            speed.addTransientModifier(new AttributeModifier(SPEED, config.speedMultiplier() - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
    }
}
