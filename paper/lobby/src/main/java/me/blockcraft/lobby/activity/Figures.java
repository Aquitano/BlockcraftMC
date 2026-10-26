package me.blockcraft.lobby.activity;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;

import java.util.function.Consumer;

/** The player-shaped figures of the lobby's activities: they stand still, cannot be hurt, and are never saved into the map. */
public final class Figures {

    private Figures() {
    }

    public static Mannequin spawn(Location location, ResolvableProfile skin, Component name) {
        return spawn(location, skin, figure -> {
            figure.customName(name);
            figure.setCustomNameVisible(true);
        });
    }

    public static Mannequin spawn(Location location, ResolvableProfile skin, Consumer<Mannequin> extra) {
        return location.getWorld().spawn(location, Mannequin.class, figure -> {
            figure.setProfile(skin);
            figure.setDescription(null);
            figure.setImmovable(true);
            figure.setGravity(false);
            figure.setInvulnerable(true);
            figure.setSilent(true);
            figure.setPersistent(false);
            figure.setRemoveWhenFarAway(false);
            extra.accept(figure);
        });
    }
}
