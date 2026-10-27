package me.blockcraft.archive.exhibit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalInt;

/** The exhibits of one old server, oldest first; the eras themselves follow in the order their servers opened. */
public record Era(String origin, List<Exhibit> exhibits) {

    private static final Comparator<OptionalInt> YEARS_FIRST =
            Comparator.comparing((OptionalInt year) -> year.isEmpty()).thenComparingInt(year -> year.orElse(0));
    private static final Comparator<Exhibit> OLDEST_FIRST =
            Comparator.comparing(Exhibit::year, YEARS_FIRST).thenComparing(Exhibit::name, String.CASE_INSENSITIVE_ORDER);

    public Era {
        exhibits = List.copyOf(exhibits);
    }

    public static List<Era> of(List<Exhibit> exhibits) {
        var byOrigin = new LinkedHashMap<String, List<Exhibit>>();
        exhibits.stream().sorted(OLDEST_FIRST).forEach(exhibit -> byOrigin
                .computeIfAbsent(exhibit.origin(), ignored -> new ArrayList<>()).add(exhibit));
        return byOrigin.entrySet().stream()
                .map(entry -> new Era(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(Era::from, YEARS_FIRST).thenComparing(Era::origin, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public OptionalInt from() {
        return exhibits.stream().map(Exhibit::year).filter(OptionalInt::isPresent).mapToInt(OptionalInt::getAsInt).min();
    }

    public OptionalInt until() {
        return exhibits.stream().map(Exhibit::year).filter(OptionalInt::isPresent).mapToInt(OptionalInt::getAsInt).max();
    }
}
