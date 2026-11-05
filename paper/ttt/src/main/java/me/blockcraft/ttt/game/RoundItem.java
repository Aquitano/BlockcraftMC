package me.blockcraft.ttt.game;

import me.blockcraft.api.item.HotbarItem;

/** The round's shop and task navigation. */
public enum RoundItem implements HotbarItem {
    SHOP, TASKS;

    public static final int SLOT = 8;
    public static final int TASKS_SLOT = 7;
}
