package me.blockcraft.signs.sign;

import org.bukkit.Location;

/** A sign that shows one server group and sends clicking players to it. */
public record StatusSign(Location location, String group) {
}
