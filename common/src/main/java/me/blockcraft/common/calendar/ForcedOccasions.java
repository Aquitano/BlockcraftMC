package me.blockcraft.common.calendar;

import me.blockcraft.common.calendar.NetworkCalendar.Occasion;
import me.blockcraft.common.moderation.Setting;
import me.blockcraft.common.moderation.SettingsRepository;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The occasions staff force with {@code /event}, kept in {@code bc_settings} so the proxy and every Paper server agree
 * and a restart keeps them: no row lets the date decide, an empty value forces nothing, otherwise the names joined by
 * commas. Names this build does not know are skipped.
 */
public final class ForcedOccasions {

    private final SettingsRepository settings;
    private final NetworkCalendar calendar;

    public ForcedOccasions(SettingsRepository settings, NetworkCalendar calendar) {
        this.settings = settings;
        this.calendar = calendar;
    }

    /** Synchronized with {@link #set}, so a reload that read the row before a change cannot undo it. */
    public synchronized void reload() {
        calendar.force(settings.get(Setting.FORCED_OCCASIONS).map(ForcedOccasions::parse));
    }

    public synchronized void set(Optional<Set<Occasion>> occasions) {
        occasions.ifPresentOrElse(chosen -> settings.set(Setting.FORCED_OCCASIONS, format(chosen)),
                () -> settings.clear(Setting.FORCED_OCCASIONS));
        calendar.force(occasions);
    }

    static Set<Occasion> parse(String value) {
        var names = List.of(value.split(","));
        return Arrays.stream(Occasion.values()).filter(occasion -> names.contains(occasion.name()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Occasion.class)));
    }

    static String format(Set<Occasion> occasions) {
        return occasions.stream().sorted().map(Occasion::name).collect(Collectors.joining(","));
    }
}
