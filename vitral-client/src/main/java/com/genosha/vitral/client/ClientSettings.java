package com.genosha.vitral.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

/**
 * What the client remembers between runs: the server address, the last e-mail used, and
 * the folders last used to save originals and to choose photos for upload.
 * Passwords and session tokens are never stored.
 */
public final class ClientSettings {

    private static final String SERVER_ADDRESS = "serverAddress";
    private static final String EMAIL = "email";
    private static final String DOWNLOAD_DIRECTORY = "downloadDirectory";
    private static final String UPLOAD_DIRECTORY = "uploadDirectory";
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
        return directory(DOWNLOAD_DIRECTORY, "Downloads");
    }

    public void rememberDownloadDirectory(Path directory) {
        preferences.put(DOWNLOAD_DIRECTORY, directory.toAbsolutePath().toString());
    }

    /** Where photos were last chosen for upload; the pictures folder, or the home folder, before that. */
    public Path uploadDirectory() {
        // "Imagens" is the pictures folder on Linux desktops set up in Portuguese.
        return directory(UPLOAD_DIRECTORY, "Imagens", "Pictures");
    }

    public void rememberUploadDirectory(Path directory) {
        preferences.put(UPLOAD_DIRECTORY, directory.toAbsolutePath().toString());
    }

    /** The saved folder if it still exists, else the first of {@code defaults} found in the home folder, else home. */
    private Path directory(String key, String... defaults) {
        String saved = preferences.get(key, null);
        if (saved != null && Files.isDirectory(Path.of(saved))) {
            return Path.of(saved);
        }
        Path home = Path.of(System.getProperty("user.home"));
        for (String name : defaults) {
            if (Files.isDirectory(home.resolve(name))) {
                return home.resolve(name);
            }
        }
        return home;
    }
}
