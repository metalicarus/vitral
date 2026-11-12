package com.genosha.vitral.client.ui;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Opens web pages in the system's browser, and builds the few links the app offers. Nothing
 * is opened unless the user clicks a link: the app itself never calls outside services.
 */
public final class Links {

    private static volatile Consumer<String> opener = url -> { };

    private Links() {
    }

    /** Set once at startup, from the application's HostServices. */
    public static void setOpener(Consumer<String> browser) {
        opener = browser;
    }

    static void open(String url) {
        opener.accept(url);
    }

    /** The position on OpenStreetMap, zoomed to street level, with a marker. */
    static String map(double latitude, double longitude) {
        String lat = String.format(Locale.ROOT, "%.6f", latitude);
        String lon = String.format(Locale.ROOT, "%.6f", longitude);
        return "https://www.openstreetmap.org/?mlat=" + lat + "&mlon=" + lon + "#map=16/" + lat + "/" + lon;
    }
}
