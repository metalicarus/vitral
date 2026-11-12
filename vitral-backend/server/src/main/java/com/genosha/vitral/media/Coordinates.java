package com.genosha.vitral.media;

/** Where a photo was taken, in decimal degrees: negative latitudes are south, negative longitudes west. */
public record Coordinates(double latitude, double longitude) {
}
