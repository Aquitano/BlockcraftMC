package me.blockcraft.bedwars.perk;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** The particles an arrow its owner shot leaves behind on every tick of its flight. */
public enum ArrowTrail implements Perk {
    HEARTS(Material.POPPY) {
        @Override
        public void spawn(Location location) {
            location.getWorld().spawnParticle(Particle.HEART, location, 1, 0, 0, 0, 0);
        }
    },
    FLAMES(Material.BLAZE_POWDER) {
        @Override
        public void spawn(Location location) {
            location.getWorld().spawnParticle(Particle.FLAME, location, 1, 0, 0, 0, 0);
        }
    },
    NOTES(Material.NOTE_BLOCK) {
        /** A note's colour comes from its x offset when the count is zero. */
        @Override
        public void spawn(Location location) {
            location.getWorld().spawnParticle(Particle.NOTE, location, 0, ThreadLocalRandom.current().nextDouble(), 0, 0, 1);
        }
    },
    SPARKS(Material.NETHER_STAR) {
        @Override
        public void spawn(Location location) {
            location.getWorld().spawnParticle(Particle.FIREWORK, location, 1, 0, 0, 0, 0);
        }
    },
    REDSTONE(Material.REDSTONE) {
        @Override
        public void spawn(Location location) {
            location.getWorld().spawnParticle(Particle.DUST, location, 1, new Particle.DustOptions(Color.RED, 1.0f));
        }
    },
    SLIME(Material.SLIME_BALL) {
        @Override
        public void spawn(Location location) {
            location.getWorld().spawnParticle(Particle.ITEM_SLIME, location, 1, 0, 0, 0, 0);
        }
    },
    LAVA(Material.LAVA_BUCKET) {
        @Override
        public void spawn(Location location) {
            location.getWorld().spawnParticle(Particle.LAVA, location, 1, 0, 0, 0, 0);
        }
    },
    RAINBOW(Material.GLOWSTONE_DUST) {
        @Override
        public void spawn(Location location) {
            var random = ThreadLocalRandom.current();
            var color = Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            location.getWorld().spawnParticle(Particle.DUST, location, 1, new Particle.DustOptions(color, 1.0f));
        }
    };

    private final Material icon;

    ArrowTrail(Material icon) {
        this.icon = icon;
    }

    public abstract void spawn(Location location);

    @Override
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public ItemStack icon() {
        return ItemStack.of(icon);
    }

    public static Optional<ArrowTrail> byKey(String key) {
        return Arrays.stream(values()).filter(trail -> trail.key().equals(key)).findFirst();
    }
}
