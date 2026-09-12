package me.blockcraft.signs.sign;

import org.bukkit.Location;

public record StatusSign(Location location, String name, String host, int port) {
}
