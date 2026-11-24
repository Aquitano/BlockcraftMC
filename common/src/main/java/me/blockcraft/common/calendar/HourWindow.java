package me.blockcraft.common.calendar;

import me.blockcraft.common.config.StrictConfig;

/** The hours from {@code fromHour} up to {@code untilHour}, which it excludes; a window that ends before it starts runs past midnight. */
public record HourWindow(int fromHour, int untilHour) {

    public HourWindow {
        fromHour = Math.clamp(fromHour, 0, 23);
        untilHour = Math.clamp(untilHour, 0, 24);
    }

    /** {@code from-hour} and {@code until-hour} of a config section. */
    public static HourWindow from(StrictConfig section) {
        return new HourWindow(section.integer("from-hour"), section.integer("until-hour"));
    }

    public boolean includes(int hour) {
        return fromHour <= untilHour ? hour >= fromHour && hour < untilHour : hour >= fromHour || hour < untilHour;
    }
}
