package com.genosha.vitral.media;

import com.genosha.vitral.asset.TakenAtSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * When a photo was taken, where that date came from, and which year/month folder it belongs in.
 *
 * @param folderDate for EXIF dates, the calendar day where the photo was taken; otherwise the day
 *                   in the server's time zone
 */
public record CaptureDate(Instant takenAt, TakenAtSource source, LocalDate folderDate) {

    /**
     * Picks the best date available: the camera's EXIF date, then the file's modification
     * time reported by the client, then the upload time.
     */
    public static CaptureDate resolve(LocalDateTime exifLocal, ZoneOffset exifOffset, Instant fileModifiedAt,
                                      Instant uploadedAt, ZoneId zone) {
        if (exifLocal != null) {
            Instant takenAt = exifOffset != null ? exifLocal.toInstant(exifOffset) : exifLocal.atZone(zone).toInstant();
            return new CaptureDate(takenAt, TakenAtSource.EXIF, exifLocal.toLocalDate());
        }
        if (fileModifiedAt != null) {
            return new CaptureDate(fileModifiedAt, TakenAtSource.FILE, LocalDate.ofInstant(fileModifiedAt, zone));
        }
        return new CaptureDate(uploadedAt, TakenAtSource.UPLOAD, LocalDate.ofInstant(uploadedAt, zone));
    }
}