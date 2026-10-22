package com.genosha.vitral.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContentDispositionTest {

    @Test
    void plainNamesAppearAsTheyAre() {
        assertEquals("inline; filename=\"IMG_1234.HEIC\"; filename*=UTF-8''IMG_1234.HEIC",
                ContentDisposition.inline("IMG_1234.HEIC"));
    }

    @Test
    void accentsAndSpacesAreEncodedInUtf8() {
        assertEquals("inline; filename=\"F_rias _ beira-mar.jpg\"; filename*=UTF-8''F%C3%A9rias%20%C3%A0%20beira-mar.jpg",
                ContentDisposition.inline("Férias à beira-mar.jpg"));
    }

    @Test
    void charactersThatCouldBreakTheHeaderAreEscaped() {
        String header = ContentDisposition.inline("a\"b\\c\r\nX-Injected: 1.jpg");

        assertEquals("inline; filename=\"a_b_c__X-Injected: 1.jpg\"; filename*=UTF-8''a%22b%5Cc%0D%0AX-Injected%3A%201.jpg",
                header);
        assertFalse(header.contains("\r") || header.contains("\n"));
    }

    @Test
    void missingNamesGetAPlaceholder() {
        assertEquals("inline; filename=\"file\"; filename*=UTF-8''file", ContentDisposition.inline(null));
    }
}