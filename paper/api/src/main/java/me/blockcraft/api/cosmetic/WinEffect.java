package me.blockcraft.api.cosmetic;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Optional;

/** Played on the winner when a round ends. */
public enum WinEffect {
    FIREWORKS("fireworks", Material.FIREWORK_ROCKET) {
        @Override
        public void play(Player winner) {
            var firework = winner.getWorld().spawn(winner.getLocation(), Firework.class);
            var meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().withColor(Color.RED, Color.YELLOW).withFade(Color.BLUE)
                    .with(FireworkEffect.Type.BALL_LARGE).withTrail().build());
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        }
    },
    STORM("storm", Material.TRIDENT) {
        @Override
        public void play(Player winner) {
            var world = winner.getWorld();
            var location = winner.getLocation();
            for (int i = 0; i < 4; i++) {
                world.strikeLightningEffect(location.clone().add(3 * Math.cos(i * Math.PI / 2), 0, 3 * Math.sin(i * Math.PI / 2)));
            }
        }
    },
    TOTEM("totem", Material.TOTEM_OF_UNDYING) {
        @Override
        public void play(Player winner) {
            winner.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, winner.getLocation().add(0, 1, 0), 150, 0.5, 1.0, 0.5, 0.3);
            winner.getWorld().playSound(winner.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        }
    },
    DRAGON("dragon", Material.DRAGON_HEAD) {
        @Override
        public void play(Player winner) {
            winner.getWorld().spawnParticle(Particle.DRAGON_BREATH, winner.getLocation().add(0, 1, 0), 120, 1.0, 0.5, 1.0, 0.05);
            winner.getWorld().playSound(winner.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        }
    },
    NOTES("notes", Material.NOTE_BLOCK) {
        @Override
        public void play(Player winner) {
            winner.getWorld().spawnParticle(Particle.NOTE, winner.getLocation().add(0, 2, 0), 40, 1.0, 0.5, 1.0, 1.0);
            winner.getWorld().playSound(winner.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
        }
    },
    CONFETTI("confetti", Material.PINK_DYE, true) {
        @Override
        public void play(Player winner) {
            var world = winner.getWorld();
            var location = winner.getLocation().add(0, 1.8, 0);
            for (var color : new Color[]{Color.RED, Color.YELLOW, Color.AQUA, Color.LIME}) {
                world.spawnParticle(Particle.DUST, location, 18, 0.8, 0.5, 0.8, 0,
                        new Particle.DustOptions(color, 1.2f));
            }
            world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.3f);
        }
    },
    AURORA("aurora", Material.AMETHYST_SHARD, true) {
        @Override
        public void play(Player winner) {
            var world = winner.getWorld();
            var location = winner.getLocation();
            var green = new Particle.DustOptions(Color.LIME, 1.4f);
            var purple = new Particle.DustOptions(Color.PURPLE, 1.4f);
            for (int i = 0; i < 32; i++) {
                double angle = i * Math.PI / 8;
                double height = 0.4 + i * 0.07;
                world.spawnParticle(Particle.DUST,
                        location.clone().add(1.2 * Math.cos(angle), height, 1.2 * Math.sin(angle)),
                        2, 0.05, 0.05, 0.05, 0, i % 2 == 0 ? green : purple);
            }
            world.spawnParticle(Particle.END_ROD, location.clone().add(0, 2.5, 0), 24, 0.6, 0.3, 0.6, 0.02);
            world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f);
        }
    };

    private final String key;
    private final Material icon;
    private final boolean earned;

    WinEffect(String key, Material icon) {
        this(key, icon, false);
    }

    WinEffect(String key, Material icon, boolean earned) {
        this.key = key;
        this.icon = icon;
        this.earned = earned;
    }

    public abstract void play(Player winner);

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
        return "effect.win." + key;
    }

    public static Optional<WinEffect> byKey(String key) {
        return Arrays.stream(values()).filter(effect -> effect.key.equals(key)).findFirst();
    }
}
