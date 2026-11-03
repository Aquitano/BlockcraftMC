package me.blockcraft.archive.exhibit;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public record ExhibitCollection(String origin, List<Exhibit> exhibits) {

    static final Comparator<Exhibit> EXHIBIT_ORDER = Comparator.comparing((Exhibit exhibit) -> exhibit.year().isEmpty())
            .thenComparingInt(exhibit -> exhibit.year().orElse(0))
            .thenComparing(Exhibit::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Exhibit::key);

    public ExhibitCollection {
        exhibits = exhibits.stream().sorted(EXHIBIT_ORDER).toList();
    }

    public static List<ExhibitCollection> of(List<Exhibit> exhibits, List<String> order) {
        return exhibits.stream().collect(Collectors.groupingBy(Exhibit::origin)).entrySet().stream()
                .map(entry -> new ExhibitCollection(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt((ExhibitCollection collection) -> {
                    int index = order.indexOf(collection.origin());
                    return index < 0 ? order.size() : index;
                }).thenComparing(ExhibitCollection::origin, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public boolean hasCategories(String fallbackCategory) {
        return exhibits.size() > 9 && exhibits.stream()
                .map(exhibit -> exhibit.category().orElse(fallbackCategory)).distinct().count() > 1;
    }
}
