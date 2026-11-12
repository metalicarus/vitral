package com.genosha.vitral.client.ui;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

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
    void countsPickSingularOrPlural() {
        assertEquals("1 foto enviada", Formats.count(1, "foto enviada", "fotos enviadas"));
        assertEquals("3 fotos enviadas", Formats.count(3, "foto enviada", "fotos enviadas"));
        assertEquals("0 fotos enviadas", Formats.count(0, "foto enviada", "fotos enviadas"));
    }

    @Test
    void uploadSummariesMentionOnlyWhatHappened() {
        assertEquals("3 fotos enviadas · 1 já estava no servidor",
                UploadBar.describe(new UploadJob.Summary(3, 1, 0, List.of(), false)));
        assertEquals("Envio cancelado. 1 foto enviada · 2 arquivos ignorados (não são fotos)",
                UploadBar.describe(new UploadJob.Summary(1, 0, 2, List.of(), true)));
        assertEquals("Nenhuma foto encontrada no que foi escolhido.",
                UploadBar.describe(new UploadJob.Summary(0, 0, 0, List.of(), false)));
    }

    @Test
    void cameraNamesDoNotRepeatTheMaker() {
        assertEquals("Canon EOS R6", Formats.camera("Canon", "Canon EOS R6"));
        assertEquals("Apple iPhone 15", Formats.camera("Apple", "iPhone 15"));
        assertEquals("Pixel 8", Formats.camera(null, "Pixel 8"));
        assertEquals("NIKON", Formats.camera("NIKON", null));
        assertNull(Formats.camera(null, null));
    }

    @Test
    void exposureReadsLikeOnTheCamera() {
        assertEquals("f/4 · 1/250 s · ISO 400 · 50 mm", Formats.exposure(4.0, 0.004, 400, 50.0));
        assertEquals("f/1,8 · 1/60 s", Formats.exposure(1.8, 1.0 / 60, null, null));
        assertEquals("2 s · 6,9 mm", Formats.exposure(null, 2.0, null, 6.86));
        assertNull(Formats.exposure(null, null, null, null));
    }

    @Test
    void coordinatesUseHemispheresInPortuguese() {
        assertEquals("23,5874° S, 46,6576° O", Formats.coordinates(-23.5874, -46.6576));
        assertEquals("48,8584° N, 2,2945° L", Formats.coordinates(48.8584, 2.2945));
    }

    @Test
    void mapLinksPointAtTheExactPosition() {
        assertEquals("https://www.openstreetmap.org/?mlat=-23.587400&mlon=-46.657600#map=16/-23.587400/-46.657600",
                Links.map(-23.5874, -46.6576));
    }

    @Test
    void dimensionsNeedBothSides() {
        assertEquals("4032 × 3024", Formats.dimensions(4032, 3024));
        assertNull(Formats.dimensions(4032, null));
    }
}
