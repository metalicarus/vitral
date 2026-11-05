package com.genosha.vitral.storage;

import java.nio.charset.StandardCharsets;

/**
 * Turns a client-supplied file name into one that is safe to create on disk,
 * on any operating system, while keeping it recognizable.
 */
public final class FileName {

    /** Most file systems allow 255 bytes per name; this leaves room for a " (999)" suffix. */
    static final int MAX_BYTES = 200;
    private static final String FALLBACK_BASE = "photo";

    private FileName() {
    }

    /**
     * Drops any directory part, removes characters that are invalid or dangerous in
     * file names, and makes sure there is an extension.
     */
    public static String sanitize(String name, String fallbackExtension) {
        String result = baseName(name);

        StringBuilder clean = new StringBuilder(result.length());
        result.codePoints().forEach(cp -> {
            if (cp < 0x20 || cp == 0x7F || "<>:\"|?*".indexOf(cp) >= 0) {
                clean.append('_');
            } else {
                clean.appendCodePoint(cp);
            }
        });
        // Windows ignores trailing dots and spaces; leading ones make hidden or odd files.
        result = clean.toString().replaceAll("^[\\s.]+|[\\s.]+$", "");

        if (result.isEmpty()) {
            result = FALLBACK_BASE;
        }
        if (extension(result).isEmpty() && fallbackExtension != null && !fallbackExtension.isEmpty()) {
            result = result + "." + fallbackExtension;
        }
        return truncate(result);
    }

    /** The name without any directory part, whether the client used / or \\; empty for null. */
    public static String baseName(String name) {
        if (name == null) {
            return "";
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return name.substring(slash + 1);
    }

    /** {@code IMG.JPG} with counter 2 becomes {@code IMG (2).JPG}; counter 0 keeps the name. */
    public static String withCounter(String name, int counter) {
        if (counter == 0) {
            return name;
        }
        String extension = extension(name);
        String base = extension.isEmpty() ? name : name.substring(0, name.length() - extension.length() - 1);
        return base + " (" + counter + ")" + (extension.isEmpty() ? "" : "." + extension);
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1) : "";
    }

    private static String truncate(String name) {
        if (utf8Length(name) <= MAX_BYTES) {
            return name;
        }
        String extension = extension(name);
        String suffix = extension.isEmpty() ? "" : "." + extension;
        String base = name.substring(0, name.length() - suffix.length());
        int budget = MAX_BYTES - utf8Length(suffix);
        StringBuilder kept = new StringBuilder();
        int used = 0;
        for (int i = 0; i < base.length(); ) {
            int cp = base.codePointAt(i);
            int size = utf8Length(new String(Character.toChars(cp)));
            if (used + size > budget) {
                break;
            }
            kept.appendCodePoint(cp);
            used += size;
            i += Character.charCount(cp);
        }
        return kept.toString().strip() + suffix;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}