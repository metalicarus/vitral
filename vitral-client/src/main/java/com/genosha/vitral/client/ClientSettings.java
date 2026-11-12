package com.genosha.vitral.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

/**
 * What the client remembers between runs: the server address, the last e-mail used and
 * the folder where originals were last saved.
 * Passwords and session tokens are never stored.
 */
public final class ClientSettings {

    private static final String SERVER_ADDRESS = "serverAddress";
    private static final String EMAIL = "email";
    private static final String DOWNLOAD_DIRECTORY = "downloadDirectory";
    private static final String DEFAULT_SERVER_ADDRESS = "http://localhost:8080";

    private final Preferences preferences;

    public ClientSettings() {
        this(Preferences.userNodeForPackage(ClientSettings.class));
    }

    ClientSettings(Preferences preferences) {
        this.preferences = preferences;
    }

    public String serverAddress() {
        return preferences.get(SERVER_ADDRESS, DEFAULT_SERVER_ADDRESS);
    }

    public String email() {
        return preferences.get(EMAIL, "");
    }

    public void remember(String serverAddress, String email) {
        preferences.put(SERVER_ADDRESS, serverAddress);
        preferences.put(EMAIL, email);
    }

    /** Where originals were last saved; the Downloads folder, or the home folder, before that. */
    public Path downloadDirectory() {
        String saved = preferences.get(DOWNLOAD_DIRECTORY, null);
        if (saved != null && Files.isDirectory(Path.of(saved))) {
            return Path.of(saved);
        }
        Path home = Path.of(System.getProperty("user.home"));
        Path downloads = home.resolve("Downloads");
        return Files.isDirectory(downloads) ? downloads : home;
    }

    public void rememberDownloadDirectory(Path directory) {
        preferences.put(DOWNLOAD_DIRECTORY, directory.toAbsolutePath().toString());
    }
}
