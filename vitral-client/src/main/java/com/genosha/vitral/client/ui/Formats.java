package com.genosha.vitral.client.ui;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** How dates, sizes and dimensions are written on screen, in Brazilian Portuguese. */
final class Formats {

    static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", PT_BR);
    private static final String[] UNITS = {"bytes", "KB", "MB", "GB", "TB"};

    private Formats() {
    }

    /** {@code Julho de 2024}. */
    static String month(YearMonth month) {
        return capitalize(MONTH.format(month));
    }

    /** {@code 14 de julho de 2024, 16:32}, in {@code zone}. */
    static String dateTime(Instant instant, ZoneId zone) {
        return DATE_TIME.format(instant.atZone(zone));
    }

    /** {@code 3,2 MB}: one decimal place from KB up, counting 1 KB as 1024 bytes. */
    static String fileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        NumberFormat number = NumberFormat.getNumberInstance(PT_BR);
        number.setMinimumFractionDigits(1);
        number.setMaximumFractionDigits(1);
        return number.format(value) + " " + UNITS[unit];
    }

    /** {@code 4032 × 3024}, or null when the server does not know the size. */
    static String dimensions(Integer width, Integer height) {
        return width == null || height == null ? null : width + " × " + height;
    }

    /**
     * {@code Canon EOS R6}: the model, prefixed by the maker unless the model already starts
     * with it, as many cameras and phones repeat it there. Null when both are unknown.
     */
    static String camera(String make, String model) {
        if (model == null) {
            return make;
        }
        if (make == null || model.toLowerCase(Locale.ROOT).startsWith(make.toLowerCase(Locale.ROOT))) {
            return model;
        }
        return make + " " + model;
    }

    /** {@code f/4 · 1/250 s · ISO 400 · 50 mm}, with whatever is known; null when nothing is. */
    static String exposure(Double fNumber, Double exposureSeconds, Integer iso, Double focalLength) {
        List<String> parts = new ArrayList<>();
        if (fNumber != null) {
            parts.add("f/" + decimal(fNumber));
        }
        if (exposureSeconds != null) {
            parts.add(shutter(exposureSeconds));
        }
        if (iso != null) {
            parts.add("ISO " + iso);
        }
        if (focalLength != null) {
            parts.add(decimal(focalLength) + " mm");
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /** Fractions of a second the way cameras show them ({@code 1/250 s}), longer times in seconds ({@code 2 s}). */
    private static String shutter(double seconds) {
        if (seconds < 1) {
            return "1/" + Math.round(1 / seconds) + " s";
        }
        return decimal(seconds) + " s";
    }

    /** {@code 23,5874° S, 46,6576° O}: four decimals are about ten meters. */
    static String coordinates(double latitude, double longitude) {
        NumberFormat number = NumberFormat.getNumberInstance(PT_BR);
        number.setMinimumFractionDigits(4);
        number.setMaximumFractionDigits(4);
        return number.format(Math.abs(latitude)) + "° " + (latitude < 0 ? "S" : "N") + ", "
                + number.format(Math.abs(longitude)) + "° " + (longitude < 0 ? "O" : "L");
    }

    /** At most one decimal place, and none when the value is whole: {@code 4}, {@code 1,8}. */
    private static String decimal(double value) {
        NumberFormat number = NumberFormat.getNumberInstance(PT_BR);
        number.setMaximumFractionDigits(1);
        return number.format(value);
    }

    /** {@code 1 foto enviada}, {@code 3 fotos enviadas}. */
    static String count(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
