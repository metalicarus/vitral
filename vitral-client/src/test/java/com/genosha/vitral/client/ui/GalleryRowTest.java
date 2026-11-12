package com.genosha.vitral.client.ui;

import com.genosha.vitral.client.api.Models.Asset;
import com.genosha.vitral.client.api.Models.TakenAtSource;
import com.genosha.vitral.client.api.Models.ThumbnailStatus;
import com.genosha.vitral.client.ui.GalleryRow.MonthHeader;
import com.genosha.vitral.client.ui.GalleryRow.Photos;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GalleryRowTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Test
    void splitsMonthsIntoRowsOfTheGivenWidth() {
        List<Asset> july = assets("2024-07-20T12:00:00Z", 5);
        List<Asset> june = assets("2024-06-10T12:00:00Z", 2);
        List<Asset> all = new ArrayList<>(july);
        all.addAll(june);

        List<GalleryRow> rows = GalleryRow.layout(all, 3, SAO_PAULO);

        assertEquals(List.of(
                new MonthHeader(YearMonth.of(2024, 7)),
                new Photos(july.subList(0, 3)),
                new Photos(july.subList(3, 5)),
                new MonthHeader(YearMonth.of(2024, 6)),
                new Photos(june)), rows);
    }

    @Test
    void usesTheLocalDayToPickTheMonth() {
        // 02:00 UTC on August 1st is still July 31st in São Paulo.
        List<Asset> asset = assets("2024-08-01T02:00:00Z", 1);

        List<GalleryRow> rows = GalleryRow.layout(asset, 4, SAO_PAULO);

        assertEquals(new MonthHeader(YearMonth.of(2024, 7)), rows.getFirst());
    }

    @Test
    void noPhotosMeansNoRows() {
        assertEquals(List.of(), GalleryRow.layout(List.of(), 4, SAO_PAULO));
    }

    @Test
    void rejectsZeroColumns() {
        assertThrows(IllegalArgumentException.class, () -> GalleryRow.layout(List.of(), 0, SAO_PAULO));
    }

    @Test
    void anotherPageOnlyChangesTheLastLines() {
        List<Asset> firstPage = assets("2024-07-20T12:00:00Z", 4);
        List<Asset> secondPage = assets("2024-07-10T12:00:00Z", 2);
        List<Asset> both = new ArrayList<>(firstPage);
        both.addAll(secondPage);

        List<GalleryRow> before = GalleryRow.layout(firstPage, 3, SAO_PAULO);
        List<GalleryRow> after = GalleryRow.layout(both, 3, SAO_PAULO);

        // Header and first full row stay; the short row of one photo grows to three.
        assertEquals(2, GalleryRow.firstDifference(before, after));
        assertEquals(before.size(), GalleryRow.firstDifference(before, before));
    }

    private static List<Asset> assets(String takenAt, int count) {
        List<Asset> assets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Instant date = Instant.parse(takenAt).minusSeconds(i);
            assets.add(new Asset(UUID.randomUUID(), "PHOTO", "IMG_" + i + ".jpg", "image/jpeg", 1000,
                    4000, 3000, date, TakenAtSource.EXIF, date, ThumbnailStatus.READY, null, null));
        }
        return assets;
    }
}
