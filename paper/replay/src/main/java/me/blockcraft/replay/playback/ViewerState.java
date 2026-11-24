package me.blockcraft.replay.playback;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

record ViewerState(Location location, GameMode gameMode, ItemStack[] contents, ItemStack cursor, int heldSlot,
                   boolean allowFlight, boolean flying, float flySpeed, float walkSpeed,
                   boolean collidable, boolean affectsSpawning, boolean invulnerable) {

    static ViewerState capture(Player viewer) {
        var cursor = viewer.getItemOnCursor().clone();
        viewer.setItemOnCursor(null);
        viewer.closeInventory();
        viewer.closeDialog();
        var contents = viewer.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            contents[i] = contents[i] == null ? null : contents[i].clone();
        }
        return new ViewerState(viewer.getLocation().clone(), viewer.getGameMode(), contents, cursor,
                viewer.getInventory().getHeldItemSlot(), viewer.getAllowFlight(), viewer.isFlying(),
                viewer.getFlySpeed(), viewer.getWalkSpeed(), viewer.isCollidable(), viewer.getAffectsSpawning(),
                viewer.isMarkedInvulnerable());
    }

    /** Puts everything back and reports whether the viewer could be moved to where they stood. */
    boolean restore(Player viewer) {
        viewer.closeInventory();
        viewer.closeDialog();
        viewer.getInventory().setContents(contents);
        viewer.getInventory().setHeldItemSlot(heldSlot);
        viewer.setItemOnCursor(cursor);
        viewer.setGameMode(gameMode);
        viewer.setAllowFlight(allowFlight);
        viewer.setFlying(allowFlight && flying);
        viewer.setFlySpeed(flySpeed);
        viewer.setWalkSpeed(walkSpeed);
        viewer.setCollidable(collidable);
        viewer.setAffectsSpawning(affectsSpawning);
        viewer.setInvulnerable(invulnerable);
        viewer.setFallDistance(0);
        return viewer.teleport(location);
    }
}
