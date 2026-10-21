package com.genosha.vitral.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTimeZoneTest {

    @Test
    void blankOrMissingMeansTheSystemTimeZone() {
        assertEquals(ZoneId.systemDefault(), new ServerTimeZone((String) null).zone());
        assertEquals(ZoneId.systemDefault(), new ServerTimeZone("").zone());
        assertEquals(ZoneId.systemDefault(), new ServerTimeZone("   ").zone());
    }

    @Test
    void acceptsIanaNamesAndOffsets() {
        assertEquals(ZoneId.of("America/Sao_Paulo"), new ServerTimeZone(" America/Sao_Paulo ").zone());
        assertEquals(ZoneId.of("-03:00"), new ServerTimeZone("-03:00").zone());
        assertEquals(ZoneId.of("UTC"), new ServerTimeZone("UTC").zone());
    }

    @Test
    void invalidValuesFailWithAClearMessage() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ServerTimeZone("Marte/Olimpo"));

        assertTrue(error.getMessage().contains("Marte/Olimpo"));
        assertTrue(error.getMessage().contains("America/Sao_Paulo"));
    }
}