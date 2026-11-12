package com.genosha.vitral.client.ui;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
