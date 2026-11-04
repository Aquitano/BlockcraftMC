package me.blockcraft.lobby.menu;

import me.blockcraft.api.cosmetic.KillEffect;
import me.blockcraft.api.cosmetic.WinEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

final class EffectPreviews {

    private EffectPreviews() {
    }

    static void kill(Player viewer, KillEffect effect) {
        var location = location(viewer);
        switch (effect) {
            case BLOOD -> viewer.spawnParticle(Particle.BLOCK, location, 40, 0.3, 0.5, 0.3,
                    Material.REDSTONE_BLOCK.createBlockData());
            case LIGHTNING -> viewer.spawnParticle(Particle.ELECTRIC_SPARK, location, 40, 0.2, 1.0, 0.2, 0.05);
            case EXPLOSION -> viewer.spawnParticle(Particle.EXPLOSION, location, 1);
            case FLAMES -> viewer.spawnParticle(Particle.FLAME, location, 50, 0.4, 0.6, 0.4, 0.05);
            case HEARTS -> viewer.spawnParticle(Particle.HEART, location, 12, 0.5, 0.5, 0.5);
            case SOUL -> viewer.spawnParticle(Particle.SOUL, location, 30, 0.3, 0.6, 0.3, 0.02);
            case STARDUST -> effect.play(viewer);
        }
    }

    static void win(Player viewer, WinEffect effect) {
        var location = location(viewer);
        switch (effect) {
            case FIREWORKS -> viewer.spawnParticle(Particle.FIREWORK, location, 70, 0.5, 0.5, 0.5, 0.1);
            case STORM -> viewer.spawnParticle(Particle.ELECTRIC_SPARK, location, 70, 0.8, 1.0, 0.8, 0.05);
            case TOTEM -> viewer.spawnParticle(Particle.TOTEM_OF_UNDYING, location, 100, 0.5, 1.0, 0.5, 0.2);
            case DRAGON -> viewer.spawnParticle(Particle.DRAGON_BREATH, location, 80, 0.7, 0.5, 0.7, 0.05);
            case NOTES -> viewer.spawnParticle(Particle.NOTE, location, 25, 0.8, 0.5, 0.8, 1.0);
            case CONFETTI, AURORA -> effect.play(viewer);
        }
    }

    private static Location location(Player viewer) {
        return viewer.getEyeLocation().add(viewer.getEyeLocation().getDirection().multiply(2.5));
    }
}
