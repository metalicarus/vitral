package com.genosha.vitral.config;

import org.sqlite.JDBC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * The SQLite JDBC driver, plus one convenience: it creates the database file's
 * directory before connecting. SQLite creates the file itself but fails when its
 * directory is missing, and the first connection happens during startup, before
 * any application code could create it.
 */
public class SqliteDriver extends JDBC {

    private static final String PREFIX = "jdbc:sqlite:";

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (acceptsURL(url)) {
            createParentDirectory(url);
        }
        return super.connect(url, info);
    }

    static void createParentDirectory(String url) throws SQLException {
        Path file = databaseFile(url);
        if (file == null) {
            return;
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new SQLException("Cannot create the database directory " + parent, e);
        }
    }

    /** The file behind a jdbc:sqlite: URL, or null for in-memory databases. */
    static Path databaseFile(String url) {
        if (url == null || !url.startsWith(PREFIX)) {
            return null;
        }
        String path = url.substring(PREFIX.length());
        int query = path.indexOf('?');
        String options = query >= 0 ? path.substring(query) : "";
        if (query >= 0) {
            path = path.substring(0, query);
        }
        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        }
        if (path.isEmpty() || path.startsWith(":memory:") || options.contains("mode=memory")) {
            return null;
        }
        return Path.of(path);
    }
}