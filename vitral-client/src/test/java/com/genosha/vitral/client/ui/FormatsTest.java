package com.genosha.vitral.client.ui;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FormatsTest {

    @Test
    void monthsAreCapitalized() {
        assertEquals("Julho de 2024", Formats.month(YearMonth.of(2024, 7)));
        assertEquals("Março de 2026", Formats.month(YearMonth.of(2026, 3)));
    }

    @Test
    void datesUseTheGivenTimeZone() {
        assertEquals("14 de julho de 2024, 16:32",
                Formats.dateTime(Instant.parse("2024-07-14T19:32:10Z"), ZoneId.of("America/Sao_Paulo")));
    }

    @Test
    void fileSizesUseBinaryUnitsAndADecimalComma() {
        assertEquals("512 bytes", Formats.fileSize(512));
        assertEquals("1,0 KB", Formats.fileSize(1024));
        assertEquals("3,2 MB", Formats.fileSize(3_355_443));
        assertEquals("4,0 GB", Formats.fileSize(4L * 1024 * 1024 * 1024));
    }

    @Test
    void dimensionsNeedBothSides() {
        assertEquals("4032 × 3024", Formats.dimensions(4032, 3024));
        assertNull(Formats.dimensions(4032, null));
    }
}
