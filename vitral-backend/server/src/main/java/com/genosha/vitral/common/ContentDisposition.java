package com.genosha.vitral.common;

import java.nio.charset.StandardCharsets;

/**
 * Builds Content-Disposition headers that keep the original file name, accents included.
 *
 * <p>The header carries the name twice: {@code filename*} in UTF-8 (RFC 8187), which
 * current browsers use, and a plain ASCII {@code filename} for older clients. Characters
 * that could break the header, such as quotes or line breaks, never reach it unescaped.
 */
public final class ContentDisposition {

    private static final String ATTR_CHARS = "!#$&+-.^_`|~";

    private ContentDisposition() {
    }

    /** Tells browsers to show the file, while still offering its name when saving it. */
    public static String inline(String fileName) {
        String name = fileName == null || fileName.isBlank() ? "file" : fileName;
        return "inline; filename=\"" + asciiFallback(name) + "\"; filename*=UTF-8''" + percentEncode(name);
    }

    static String asciiFallback(String name) {
        StringBuilder out = new StringBuilder(name.length());
        name.codePoints().forEach(cp -> out.append(cp >= 0x20 && cp < 0x7F && cp != '"' && cp != '\\'
                ? (char) cp : '_'));
        return out.toString();
    }

    static String percentEncode(String name) {
        StringBuilder out = new StringBuilder();
        for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean attrChar = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || ATTR_CHARS.indexOf(c) >= 0;
            if (attrChar) {
                out.append((char) c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return out.toString();
    }
}