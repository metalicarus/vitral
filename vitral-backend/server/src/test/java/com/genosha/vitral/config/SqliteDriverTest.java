package com.genosha.vitral.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDriverTest {

    @TempDir
    Path dir;

    @Test
    void createsTheMissingDirectoryBeforeConnecting() throws Exception {
        Path database = dir.resolve("does/not/exist/yet/vitral.db");

        try (Connection connection = new SqliteDriver().connect("jdbc:sqlite:" + database, new Properties())) {
            connection.createStatement().execute("CREATE TABLE t (x INTEGER)");
        }

        assertTrue(Files.exists(database));
    }

    @Test
    void understandsTheUrlForms() {
        assertEquals(Path.of("data/vitral.db"), SqliteDriver.databaseFile("jdbc:sqlite:data/vitral.db"));
        assertEquals(Path.of("data/vitral.db"), SqliteDriver.databaseFile("jdbc:sqlite:file:data/vitral.db?cache=shared"));
        assertNull(SqliteDriver.databaseFile("jdbc:sqlite::memory:"));
        assertNull(SqliteDriver.databaseFile("jdbc:sqlite:file:test?mode=memory&cache=shared"));
        assertNull(SqliteDriver.databaseFile("jdbc:postgresql://localhost/vitral"));
    }
}