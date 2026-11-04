package me.blockcraft.sternenfront.game;

import org.bukkit.entity.LivingEntity;

/** An explosion for {@link SternenfrontGame#blastAround}: in whose name, how far, how hard at its centre, and whether it also hurts {@code by}. */
public record Blast(LivingEntity by, Faction faction, double radius, double damage, boolean hurtsOwner) {

    private static final double EDGE_SHARE = 1.0 / 3;

    double damageAt(double distance) {
        return damage * (1 - (1 - EDGE_SHARE) * distance / radius);
    }
}
