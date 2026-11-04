package me.blockcraft.archive.exhibit;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public record Section(String origin, String category, List<Exhibit> exhibits) {

    public Section {
        exhibits = List.copyOf(exhibits);
    }

    public static List<Section> of(List<Exhibit> exhibits, String fallbackCategory) {
        record Key(String origin, String category) {}
        return exhibits.stream().collect(Collectors.groupingBy(exhibit ->
                        new Key(exhibit.origin(), exhibit.category().orElse(fallbackCategory))))
                .entrySet().stream()
                .map(entry -> new Section(entry.getKey().origin(), entry.getKey().category(),
                        entry.getValue().stream().sorted(ExhibitCollection.EXHIBIT_ORDER).toList()))
                .sorted(Comparator.comparing(Section::category, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Section::origin, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
