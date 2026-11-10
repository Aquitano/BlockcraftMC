package me.blockcraft.common.calendar;

import java.util.Map;

/** The hours from {@code fromHour} up to {@code untilHour}, which it excludes; a window that ends before it starts runs past midnight. */
public record HourWindow(int fromHour, int untilHour) {

    public HourWindow {
        fromHour = Math.clamp(fromHour, 0, 23);
        untilHour = Math.clamp(untilHour, 0, 24);
    }

    /** {@code from-hour} and {@code until-hour} of a config section, each falling back to the given hour. */
    public static HourWindow fromYaml(Map<String, Object> section, int fromHour, int untilHour) {
        return new HourWindow(hour(section, "from-hour", fromHour), hour(section, "until-hour", untilHour));
    }

    public boolean includes(int hour) {
        return fromHour <= untilHour ? hour >= fromHour && hour < untilHour : hour >= fromHour || hour < untilHour;
    }

    private static int hour(Map<String, Object> section, String key, int fallback) {
        return section.get(key) instanceof Number number ? number.intValue() : fallback;
    }
}
