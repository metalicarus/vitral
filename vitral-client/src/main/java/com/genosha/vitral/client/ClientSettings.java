package com.genosha.vitral.client;

import java.util.prefs.Preferences;

/**
 * What the client remembers between runs: the server address and the last e-mail used.
 * Passwords and session tokens are never stored.
 */
public final class ClientSettings {

    private static final String SERVER_ADDRESS = "serverAddress";
    private static final String EMAIL = "email";
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
}
