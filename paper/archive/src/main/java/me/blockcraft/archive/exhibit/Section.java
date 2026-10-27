package me.blockcraft.archive.exhibit;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

public record Section(String origin, OptionalInt year, String category, List<Exhibit> exhibits) {

    public Section {
        exhibits = List.copyOf(exhibits);
    }

    public static List<Section> of(List<Exhibit> exhibits, String fallbackCategory) {
        record Key(String origin, OptionalInt year, String category) {}
        return exhibits.stream().collect(Collectors.groupingBy(exhibit ->
                        new Key(exhibit.origin(), exhibit.year(), exhibit.category().orElse(fallbackCategory))))
                .entrySet().stream()
                .map(entry -> new Section(entry.getKey().origin(), entry.getKey().year(), entry.getKey().category(),
                        entry.getValue().stream().sorted(Comparator.comparing(Exhibit::name, String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(Exhibit::key)).toList()))
                .sorted(Comparator.comparing((Section section) -> section.year().isEmpty())
                        .thenComparingInt(section -> section.year().orElse(0))
                        .thenComparing(Section::category, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Section::origin, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
