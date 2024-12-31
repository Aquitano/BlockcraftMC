package me.blockcraft.duell.listeners;

import me.blockcraft.duell.Duell;
import org.bukkit.event.Listener;

public class MainJoinListener implements Listener {
    public static Duell main;

    public MainJoinListener(final Duell main) {
        MainJoinListener.main = main;
    }
}
