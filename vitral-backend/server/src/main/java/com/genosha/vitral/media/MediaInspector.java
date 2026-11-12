package com.genosha.vitral.media;

import com.drew.imaging.FileType;
import com.drew.imaging.FileTypeDetector;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.webp.WebpDirectory;
import com.genosha.vitral.asset.AssetType;
import jakarta.inject.Singleton;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Optional;
import java.util.Set;

/**
 * Identifies photos by their content (never by extension) and reads the metadata fields
 * the catalog keeps: dimensions, capture date, camera and exposure, and GPS position.
 */
@Singleton
public class MediaInspector {

    private static final DateTimeFormatter EXIF_DATE =
            DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    /** HEIF major brands used for HEVC-encoded images, which go by the name HEIC. */
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "hevc", "hevx");

    private record Format(String mimeType, String extension) {
    }

    private record Dimensions(Integer width, Integer height) {
    }

    private record Captured(LocalDateTime local, ZoneOffset offset) {
    }

    /** Empty when the file is not one of the accepted photo formats. */
    public Optional<MediaInfo> inspect(Path file) {
        FileType fileType;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            fileType = FileTypeDetector.detectFileType(in);
        } catch (IOException e) {
            return Optional.empty();
        }
        Format format = switch (fileType) {
            case Jpeg -> new Format("image/jpeg", "jpg");
            case Png -> new Format("image/png", "png");
            case WebP -> new Format("image/webp", "webp");
            case Heif -> isHeic(file) ? new Format("image/heic", "heic") : new Format("image/heif", "heif");
            default -> null;
        };
        if (format == null) {
            return Optional.empty();
        }

        Metadata metadata = readMetadata(file);
        Dimensions dimensions = dimensions(fileType, metadata);
        if (isRotatedSideways(metadata)) {
            dimensions = new Dimensions(dimensions.height(), dimensions.width());
        }
        Captured captured = captureDate(metadata);
        return Optional.of(new MediaInfo(AssetType.PHOTO, format.mimeType(), format.extension(),
                dimensions.width(), dimensions.height(), captured.local(), captured.offset(),
                cameraSettings(metadata), location(metadata)));
    }

    /** Camera, lens and exposure, from the main EXIF directories. */
    static CameraSettings cameraSettings(Metadata metadata) {
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (ifd0 == null && exif == null) {
            return CameraSettings.NONE;
        }
        return new CameraSettings(
                text(ifd0, ExifDirectoryBase.TAG_MAKE),
                text(ifd0, ExifDirectoryBase.TAG_MODEL),
                text(exif, ExifDirectoryBase.TAG_LENS_MODEL),
                positiveNumber(exif, ExifDirectoryBase.TAG_FNUMBER),
                positiveNumber(exif, ExifDirectoryBase.TAG_EXPOSURE_TIME),
                positiveInteger(exif, ExifDirectoryBase.TAG_ISO_EQUIVALENT),
                positiveNumber(exif, ExifDirectoryBase.TAG_FOCAL_LENGTH));
    }

    /**
     * The GPS position, or null. Phones without a fix sometimes write 0,0, a point in the
     * ocean off Africa; that is treated as no position.
     */
    static Coordinates location(Metadata metadata) {
        GpsDirectory gps = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        GeoLocation geo = gps == null ? null : gps.getGeoLocation();
        if (geo == null || geo.isZero() || Double.isNaN(geo.getLatitude()) || Double.isNaN(geo.getLongitude())
                || Math.abs(geo.getLatitude()) > 90 || Math.abs(geo.getLongitude()) > 180) {
            return null;
        }
        return new Coordinates(geo.getLatitude(), geo.getLongitude());
    }

    /** A trimmed text tag, or null when absent or blank. Cameras often pad these with spaces or NULs. */
    private static String text(Directory directory, int tag) {
        if (directory == null) {
            return null;
        }
        String value = directory.getString(tag);
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\0', ' ').strip();
        return cleaned.isEmpty() || cleaned.length() > 200 ? null : cleaned;
    }

    private static Double positiveNumber(Directory directory, int tag) {
        if (directory == null || !directory.containsTag(tag)) {
            return null;
        }
        try {
            double value = directory.getDouble(tag);
            return value > 0 && Double.isFinite(value) ? value : null;
        } catch (MetadataException e) {
            return null;
        }
    }

    private static Integer positiveInteger(Directory directory, int tag) {
        Integer value = directory == null ? null : directory.getInteger(tag);
        return value != null && value > 0 ? value : null;
    }

    private static Metadata readMetadata(Path file) {
        try {
            return ImageMetadataReader.readMetadata(file.toFile());
        } catch (ImageProcessingException | IOException | RuntimeException e) {
            // A photo with broken metadata is still a photo; it just gets fewer details.
            return new Metadata();
        }
    }

    private static Dimensions dimensions(FileType fileType, Metadata metadata) {
        return switch (fileType) {
            case Jpeg -> fromDirectory(metadata.getFirstDirectoryOfType(JpegDirectory.class),
                    JpegDirectory.TAG_IMAGE_WIDTH, JpegDirectory.TAG_IMAGE_HEIGHT);
            case Png -> {
                for (PngDirectory directory : metadata.getDirectoriesOfType(PngDirectory.class)) {
                    if (directory.containsTag(PngDirectory.TAG_IMAGE_WIDTH)) {
                        yield fromDirectory(directory, PngDirectory.TAG_IMAGE_WIDTH, PngDirectory.TAG_IMAGE_HEIGHT);
                    }
                }
                yield new Dimensions(null, null);
            }
            case WebP -> fromDirectory(metadata.getFirstDirectoryOfType(WebpDirectory.class),
                    WebpDirectory.TAG_IMAGE_WIDTH, WebpDirectory.TAG_IMAGE_HEIGHT);
            // The HEIF container's own size can't be trusted: phones split large images into
            // tiles, and encoders pad the image to whole blocks and store the real crop
            // elsewhere, so it may report a tile's or the padded size. Phones also write the
            // real size to EXIF; without it the size stays unknown until the image is decoded.
            case Heif -> fromDirectory(metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class),
                    ExifDirectoryBase.TAG_EXIF_IMAGE_WIDTH, ExifDirectoryBase.TAG_EXIF_IMAGE_HEIGHT);
            default -> new Dimensions(null, null);
        };
    }

    private static Dimensions fromDirectory(Directory directory, int widthTag, int heightTag) {
        if (directory == null) {
            return new Dimensions(null, null);
        }
        return new Dimensions(positive(directory.getInteger(widthTag)), positive(directory.getInteger(heightTag)));
    }

    private static Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    /** EXIF orientations 5 to 8 mean the camera was turned 90 degrees. */
    private static boolean isRotatedSideways(Metadata metadata) {
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        Integer orientation = ifd0 == null ? null : ifd0.getInteger(ExifDirectoryBase.TAG_ORIENTATION);
        return orientation != null && orientation >= 5 && orientation <= 8;
    }

    /** Takes the first date found: when the photo was taken, then digitized, then last modified by the camera. */
    private static Captured captureDate(Metadata metadata) {
        int[][] candidates = {
                {ExifDirectoryBase.TAG_DATETIME_ORIGINAL, ExifDirectoryBase.TAG_TIME_ZONE_ORIGINAL},
                {ExifDirectoryBase.TAG_DATETIME_DIGITIZED, ExifDirectoryBase.TAG_TIME_ZONE_DIGITIZED},
        };
        for (int[] tags : candidates) {
            for (ExifSubIFDDirectory directory : metadata.getDirectoriesOfType(ExifSubIFDDirectory.class)) {
                LocalDateTime local = parseExifDate(directory.getString(tags[0]));
                if (local != null) {
                    return new Captured(local, parseOffset(directory.getString(tags[1])));
                }
            }
        }
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        LocalDateTime modified = ifd0 == null ? null : parseExifDate(ifd0.getString(ExifDirectoryBase.TAG_DATETIME));
        if (modified != null) {
            ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            ZoneOffset offset = subIfd == null ? null : parseOffset(subIfd.getString(ExifDirectoryBase.TAG_TIME_ZONE));
            return new Captured(modified, offset);
        }
        return new Captured(null, null);
    }

    /** EXIF dates look like {@code 2024:07:14 16:32:10}; cameras without a clock write zeros. */
    static LocalDateTime parseExifDate(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim().replaceFirst("^(\\d{4})-(\\d{2})-(\\d{2})", "$1:$2:$3");
        if (text.length() > 19) {
            text = text.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(text, EXIF_DATE);
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** EXIF offsets look like {@code -03:00}. */
    static ZoneOffset parseOffset(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZoneOffset.of(value.trim());
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** Reads the HEIF major brand: bytes 8 to 11 of the file, right after "ftyp". */
    private static boolean isHeic(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] header = in.readNBytes(12);
            if (header.length < 12) {
                return false;
            }
            String brand = new String(header, 8, 4, StandardCharsets.US_ASCII);
            return HEIC_BRANDS.contains(brand);
        } catch (IOException e) {
            return false;
        }
    }
}