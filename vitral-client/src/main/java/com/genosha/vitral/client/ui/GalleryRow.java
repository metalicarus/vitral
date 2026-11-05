package com.genosha.vitral.client.ui;

import com.genosha.vitral.client.api.Models.Asset;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * One line of the gallery: either the title of a month or a row of photos taken in it.
 *
 * <p>The gallery is a list of these lines rather than one big grid, so the list only keeps
 * nodes and images for the lines on screen, however many photos the library holds.
 */
public sealed interface GalleryRow {

    record MonthHeader(YearMonth month) implements GalleryRow {
    }

    record Photos(List<Asset> assets) implements GalleryRow {

        public Photos {
            assets = List.copyOf(assets);
        }
    }

    /**
     * Splits photos, already sorted newest first, into months and rows of {@code columns} photos.
     * A month's last row may be shorter; photos of different months never share a row.
     *
     * @param zone the time zone that decides which day, and so which month, a photo belongs to
     */
    static List<GalleryRow> layout(List<Asset> assets, int columns, ZoneId zone) {
        if (columns < 1) {
            throw new IllegalArgumentException("columns must be at least 1");
        }
        List<GalleryRow> rows = new ArrayList<>();
        YearMonth currentMonth = null;
        List<Asset> currentRow = new ArrayList<>(columns);
        for (Asset asset : assets) {
            YearMonth month = YearMonth.from(LocalDate.ofInstant(asset.takenAt(), zone));
            if (!month.equals(currentMonth)) {
                if (!currentRow.isEmpty()) {
                    rows.add(new Photos(currentRow));
                    currentRow.clear();
                }
                rows.add(new MonthHeader(month));
                currentMonth = month;
            }
            currentRow.add(asset);
            if (currentRow.size() == columns) {
                rows.add(new Photos(currentRow));
                currentRow.clear();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(new Photos(currentRow));
        }
        return rows;
    }

    /** Where {@code next} starts to differ from {@code current}; equal lists give their size. */
    static int firstDifference(List<GalleryRow> current, List<GalleryRow> next) {
        int common = Math.min(current.size(), next.size());
        for (int i = 0; i < common; i++) {
            if (!current.get(i).equals(next.get(i))) {
                return i;
            }
        }
        return common;
    }
}
