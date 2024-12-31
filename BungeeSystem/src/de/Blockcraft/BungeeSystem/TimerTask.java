package de.Blockcraft.BungeeSystem;

import de.Blockcraft.BungeeSystem.Commands.JoinMe;

public class TimerTask
        extends java.util.TimerTask {
    @Override
    public void run() {
        JoinMe.used.clear();
    }
}

