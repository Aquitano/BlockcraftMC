package me.blockcraft.common.elo;

import java.time.LocalDate;

/** A three-month ranked season starting in January, April, July and October; the key is what {@code bc_elo} stores. */
public record Season(int year, int quarter) {

    private static final int MONTHS = 3;

    public static Season of(LocalDate date) {
        return new Season(date.getYear(), (date.getMonthValue() - 1) / MONTHS + 1);
    }

    public static Season current() {
        return of(LocalDate.now());
    }

    public String key() {
        return year + "-" + quarter;
    }
}
