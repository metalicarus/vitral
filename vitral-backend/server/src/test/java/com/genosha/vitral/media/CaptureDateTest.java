package com.genosha.vitral.media;

import com.genosha.vitral.asset.TakenAtSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureDateTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Instant UPLOADED = Instant.parse("2026-10-20T12:00:00Z");
    private static final LocalDateTime NEW_YEARS_EVE = LocalDateTime.of(2024, 12, 31, 23, 30);

    @Test
    void exifWithOffsetUsesTheCamerasOffset() {
        CaptureDate date = CaptureDate.resolve(NEW_YEARS_EVE, ZoneOffset.ofHours(9), null, UPLOADED, SAO_PAULO);

        assertEquals(Instant.parse("2024-12-31T14:30:00Z"), date.takenAt());
        assertEquals(TakenAtSource.EXIF, date.source());
        // The folder follows the day where the photo was taken, not the server's day.
        assertEquals(LocalDate.of(2024, 12, 31), date.folderDate());
    }

    @Test
    void exifWithoutOffsetAssumesTheServerTimeZone() {
        CaptureDate date = CaptureDate.resolve(NEW_YEARS_EVE, null, null, UPLOADED, SAO_PAULO);

        assertEquals(Instant.parse("2025-01-01T02:30:00Z"), date.takenAt());
        assertEquals(LocalDate.of(2024, 12, 31), date.folderDate());
    }

    @Test
    void exifWinsOverTheFileDate() {
        CaptureDate date = CaptureDate.resolve(NEW_YEARS_EVE, null, Instant.parse("2020-01-01T00:00:00Z"),
                UPLOADED, SAO_PAULO);

        assertEquals(TakenAtSource.EXIF, date.source());
    }

    @Test
    void fileDateFoldersFollowTheServerTimeZone() {
        CaptureDate date = CaptureDate.resolve(null, null, Instant.parse("2025-01-01T01:00:00Z"), UPLOADED, SAO_PAULO);

        assertEquals(TakenAtSource.FILE, date.source());
        assertEquals(Instant.parse("2025-01-01T01:00:00Z"), date.takenAt());
        // Still Dec 31 in São Paulo.
        assertEquals(LocalDate.of(2024, 12, 31), date.folderDate());
    }

    @Test
    void uploadTimeIsTheLastResort() {
        CaptureDate date = CaptureDate.resolve(null, null, null, UPLOADED, SAO_PAULO);

        assertEquals(TakenAtSource.UPLOAD, date.source());
        assertEquals(UPLOADED, date.takenAt());
    }
}