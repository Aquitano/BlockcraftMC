package me.blockcraft.api.cosmetic;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Optional;

/** Played where a victim died, for everyone nearby. */
public enum KillEffect {
    BLOOD("blood", Material.REDSTONE) {
        @Override
        public void play(Player victim) {
            victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1, 0), 60, 0.3, 0.5, 0.3, Material.REDSTONE_BLOCK.createBlockData());
        }
    },
    LIGHTNING("lightning", Material.LIGHTNING_ROD) {
        @Override
        public void play(Player victim) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
        }
    },
    EXPLOSION("explosion", Material.TNT) {
        @Override
        public void play(Player victim) {
            victim.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, victim.getLocation().add(0, 1, 0), 1);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        }
    },
    FLAMES("flames", Material.BLAZE_POWDER) {
        @Override
        public void play(Player victim) {
            victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().add(0, 1, 0), 80, 0.4, 0.6, 0.4, 0.05);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
        }
    },
    HEARTS("hearts", Material.POPPY) {
        @Override
        public void play(Player victim) {
            victim.getWorld().spawnParticle(Particle.HEART, victim.getLocation().add(0, 1.5, 0), 20, 0.5, 0.5, 0.5);
        }
    },
    SOUL("soul", Material.SOUL_LANTERN) {
        @Override
        public void play(Player victim) {
            victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.02);
            victim.getWorld().playSound(victim.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 2.0f, 0.8f);
        }
    },
    STARDUST("stardust", Material.GLOWSTONE_DUST, true) {
        @Override
        public void play(Player victim) {
            var world = victim.getWorld();
            var location = victim.getLocation().add(0, 1, 0);
            world.spawnParticle(Particle.END_ROD, location, 36, 0.4, 0.7, 0.4, 0.04);
            world.spawnParticle(Particle.ENCHANT, location, 24, 0.5, 0.5, 0.5, 0.15);
            world.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
        }
    };

    private final String key;
    private final Material icon;
    private final boolean earned;

    KillEffect(String key, Material icon) {
        this(key, icon, false);
    }

    KillEffect(String key, Material icon, boolean earned) {
        this.key = key;
        this.icon = icon;
        this.earned = earned;
    }

    public abstract void play(Player victim);

    public String key() {
        return key;
    }

    public Material icon() {
        return icon;
    }

    public boolean earned() {
        return earned;
    }

    public String item() {
        return "effect.kill." + key;
    }

    public static Optional<KillEffect> byKey(String key) {
        return Arrays.stream(values()).filter(effect -> effect.key.equals(key)).findFirst();
    }

}
